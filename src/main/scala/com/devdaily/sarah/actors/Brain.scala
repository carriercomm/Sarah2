package com.devdaily.sarah.actors

import scala.util.Random
import scala.collection.mutable.ListBuffer
import collection.JavaConversions._
import java.util._
import java.io.IOException
import java.io.File
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.sound.sampled._
import _root_.com.devdaily.sarah._
import _root_.com.devdaily.sarah.plugins._
import scala.io.Source
import scala.collection.mutable.ArrayBuffer
import javax.swing.SwingUtilities
import akka.actor._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import grizzled.slf4j.Logging

object Brain {

  val SHORT_DELAY = 500
  val REPLY_TO_THANK_YOU_FILE = "thank_you_replies.data"  // when the user says "thank you"
  val REPLY_TO_COMPUTER_FILE  = "computer_replies.data"   // when the user says "computer"
  val SAY_YES_FILE            = "say_yes.data"            // different ways of saying "yes"
    
  // mouth states
  val MOUTH_STATE_SPEAKING     = 1
  val MOUTH_STATE_NOT_SPEAKING = 2
  
  // ear states
  val EARS_STATE_LISTENING       = 100  
  val EARS_STATE_NOT_LISTENING   = 200  
  val EARS_STATE_HEARD_SOMETHING = 300
  
  // sarah's states of being awake of asleep
  val AWARENESS_STATE_AWAKE       = 10
  val AWARENESS_STATE_LIGHT_SLEEP = 20
  val AWARENESS_STATE_DEEP_SLEEP  = 30
  
}

/**
 * The Brain has the responsibility of deciphering whatever input it
 * is given, then taking action on that input, as desired.
 * 
 * The Ears send us whatever they hear. If they hear something we just
 * said, we should ignore it. If Sarah is sleeping, we'll wake up if
 * they heard "wake up".
 * 
 * This actor has the responsibility of running whatever command it is given.
 * If necessary, the Brain will also tell the Mouth what to say, so when
 * running iTunes, the Brain may tell the Mouth to say that it's about to
 * run iTunes, and then it will do whatever it needs to do to run
 * iTunes.
 */
class Brain(sarah: Sarah) 
extends Actor
with Logging
{
  
//  val log = Logger("Brain")

  // actors we collaborate with
  var mouth: ActorRef = _
  val brainSomethingWasHeardHelper = context.actorOf(Props(new BrainSomethingWasHeardHelper(sarah)), name = "BrainSomethingWasHeardHelper")
  
  // i use this in a future call below. if that call isn't needed, this reference isn't needed
  implicit val system = context.system
  
  // states we maintain
  private var mouthIsSpeaking = false
  private var mouthState = Brain.MOUTH_STATE_NOT_SPEAKING
  private var awarenessState = Brain.AWARENESS_STATE_AWAKE
  
  private var akkaPluginInstances = ArrayBuffer[SarahAkkaActorBasedPlugin]()
  private var akkaPluginReferences = ArrayBuffer[ActorRef]()

  def receive = {
    
    // initialization
    
    case ConnectToSiblings =>
         handleConnectToSiblingsMessage

    case SetMinimumWaitTimeAfterSpeaking(waitTime) =>
         setMinimumWaitAfterSpeakingTime(waitTime)
         
    // actions

    case message: MessageFromEars =>
         logger.info(format("%d - got MessageFromEars", getCurrentTime))
         logger.info(format("    message was: " + message.textFromUser))
         handleMessageFromEars(message)

    case pleaseSay: PleaseSay =>
         logger.info(format("%d - got PleaseSay request", getCurrentTime))
         handlePleaseSayRequest(pleaseSay)

    case playSoundFileRequest: PlaySoundFileRequest =>
         logger.info(format("%d - got PlaySoundFileRequest", getCurrentTime))
         handlePlaySoundFileRequest(playSoundFileRequest)
         
    case HeresANewPlugin(pluginRef) =>
         handleNewPluginRef(pluginRef)

    // state
    case MouthIsSpeaking =>
         handleMouthIsSpeakingMessage
         
    case MouthIsFinishedSpeaking =>
         handleMouthIsFinishedSpeakingMessage
         
    case SetAwarenessState(state) =>
         setAwarenessState(state)
         
    case SetMouthState(state) =>
         setMouthState(state)
         
    case SetBrainStates(awareness, ears, mouth) =>
         setStates(awareness, ears, mouth)

    case GetAwarenessState => sender ! getAwarenessState
    case GetMouthState => sender ! getMouthState
    case GetInSleepMode => sender ! inSleepMode
    
    // other
    
    case Die =>
         logger.info("*** GOT DIE MESSAGE, EXITING ***")
         context.stop(self)

    case unknown => 
         logger.info(format("got an unknown request(%s), ignoring it", unknown.toString))
  }
  
  def handleNewPluginRef(pluginRef: ActorRef) {
    akkaPluginReferences += pluginRef
  }
  
//  def startAkkaPlugin(pluginInstance: SarahAkkaActorBasedPlugin) {
//    // create the plugin, then give it a reference to the brain
//    val pluginRef = context.actorOf(Props(pluginInstance), name = pluginInstance.getClass.getName)
//    pluginRef ! StartPluginMessage(self)
//    akkaPluginReferences += pluginRef
//  }
//  
  def handleConnectToSiblingsMessage {
    mouth = context.actorFor("../Mouth")
  }

  def handleMouthIsSpeakingMessage {
    mouthIsSpeaking = true
  }
  
  def handleMouthIsFinishedSpeakingMessage {
    markThisAsTheLastTimeSarahSpoke
    mouthIsSpeaking = false
    tellUISpeakingHasEnded
  }
  

  /**
   * State Management Code 
   * ---------------------------------------
   */
  
  def getMouthState = mouthState
  def getAwarenessState = awarenessState
  
  def setAwarenessState(state: Int) {
    awarenessState = state
    updateSarahsUI
  }
  
  def setMouthState(state: Int) {
    mouthState = state
    updateSarahsUI
  }

  // use this method when setting multiple states at the same time
  def setStates(awareness: Int, ears: Int, mouth: Int) {
    awarenessState = awareness
    mouthState = mouth
    updateSarahsUI
  }

  // needed for Future use
  import scala.concurrent.ExecutionContext.Implicits.global
  
  def tellUISpeakingHasEnded {
    val f1 = Future {  
      SwingUtilities.invokeLater(new Runnable() 
      {
        def run
        {
          sarah.updateUISpeakingHasEnded
        }
      });
    }
  }
  
  def updateSarahsUI {
    val f1 = Future {  
      SwingUtilities.invokeLater(new Runnable() 
      {
        def run
        {
          sarah.updateUI
        }
      });
    }
  }
  
  def inSleepMode = if (getAwarenessState == Brain.AWARENESS_STATE_AWAKE) false else true
  def sarahIsAwake = if (getAwarenessState == Brain.AWARENESS_STATE_AWAKE) false else true
  def sarahIsInLightSleep = if (getAwarenessState == Brain.AWARENESS_STATE_LIGHT_SLEEP) false else true
  def sarahIsInDeepSleep = if (getAwarenessState == Brain.AWARENESS_STATE_DEEP_SLEEP) false else true

  // use these two to help track when sarah last spoke.
  private var lastTimeSarahSpoke = System.currentTimeMillis 
  def getCurrentTime = System.currentTimeMillis

  /**
   * determine whether we think sarah just finished speaking.
   * if so, return true, else false.
   */
  def sarahJustFinishedSpeaking: Boolean = {
    val timeSinceSarahLastSpoke = getCurrentTime - lastTimeSarahSpoke
    logger.info(format("timeSinceSarahLastSpoke = %d", timeSinceSarahLastSpoke))
    if (timeSinceSarahLastSpoke < minimumWaitTime) true else false
  }

  // let sarah set this with her new properties file
  var minimumWaitTime = 1250
  def setMinimumWaitAfterSpeakingTime(t: Int) {
    minimumWaitTime = t
  }

  def markThisAsTheLastTimeSarahSpoke {
    lastTimeSarahSpoke = getCurrentTime
    logger.info(format("lastTimeSarahSpoke = %d", getCurrentTime))
  }
  
  def handleMessageFromEars(message: MessageFromEars) {
    logger.info("entered handleMessageFromEars")
    if (mouthIsSpeaking) {
        logger.info(format("sarah is speaking, ignoring message from ears (%s)", message.textFromUser))
    }
    else if (sarahJustFinishedSpeaking) {
        logger.info(format("sarah just spoke, ignoring message from ears (%s)", message.textFromUser))
    } 
    else {
        logger.info(format("passing MessageFromEars to brainSomethingWasHeardHelper (%s)", message.textFromUser))
        brainSomethingWasHeardHelper ! SomethingWasHeard(message.textFromUser, inSleepMode, awarenessState)
    }
  }
  
  // all we do now is pass this on to another actor
  private def handlePleaseSayRequest(pleaseSay: PleaseSay) {
    if (inSleepMode) {
      logger.info(format("in sleep mode, NOT passing on PleaseSay request (%s)", pleaseSay.textToSay))
    }
    else {
      logger.info(format("passing PleaseSay request to brainSomethingWasHeardHelper (%s)", pleaseSay.textToSay))
      brainSomethingWasHeardHelper ! pleaseSay
    }
  }

  private def handlePlaySoundFileRequest(playSoundFileRequest: PlaySoundFileRequest) {
    if (inSleepMode) {
      logger.info(format("in sleep mode, NOT passing on PlaySoundFile request (%s)", playSoundFileRequest.soundFile))
    }
    else {
      logger.info(format("passing SoundFileRequest to Mouth (%s)", playSoundFileRequest.soundFile))
      brainSomethingWasHeardHelper ! playSoundFileRequest
    }
  }

  
}







