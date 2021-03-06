package com.devdaily.sarah.gui

import com.devdaily.sarah.Sarah
import javax.swing.SwingUtilities
import com.devdaily.sarah.plugins.PluginUtils
import com.devdaily.sarah.actors.Brain
import com.devdaily.sarah.MainFrame2
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ActionListener
import java.awt.event.ActionEvent
import javax.swing.event.DocumentListener
import javax.swing.event.DocumentEvent
import javax.swing.JOptionPane
import javax.swing.Timer

class Sarah2MainFrameController(sarah: Sarah)
extends BaseMainFrameController {

    val mainFrame = new MainFrame2("")
    val textField = mainFrame.getTextField
    
    // use these fields so i don't send the text to sarah until the 'insert' is complete
    var lastChange = System.currentTimeMillis

    /**
     * this is (was?) needed to re-display the window. after (a) it is hidden with Cmd-H and
     * then (b) re-displayed with Cmd-Tab. without this, the jframe is not visible.
     */
    mainFrame.addComponentListener(new ComponentAdapter() {
        override def componentShown(e: ComponentEvent) {
            mainFrame.setVisible(true)
        }
    })

    /**
     * Timer code
     * ----------
     */
    val waitTime = 1500
    var theTextFieldSeemsToBeChanging = false
    var timer = new Timer(waitTime, null)  // dangerous null use, but new timer is always created in the DocumentListener

    // timer doc: http://docs.oracle.com/javase/tutorial/uiswing/misc/timer.html
    // this is triggered when the timer goes off
    val listener = new ActionListener {
        def actionPerformed(ae: ActionEvent){
            sarah.sendPhraseToBrain(textField.getText)
            println("CALLING BRAIN WITH TEXT = " + textField.getText)
            theTextFieldSeemsToBeChanging = false
            timer.stop
        }
    }

    textField.getDocument.addDocumentListener(new DocumentListener() {
        def insertUpdate(e: DocumentEvent) {
            if (theTextFieldSeemsToBeChanging) {
                timer.restart
                println("TEXTFIELD IS CHANGING, TEXT: " + textField.getText)
            } else {
                // textfield hasn't changed in a while, and just received its first insert event
                println("STARTING NEW TIMER, TEXT = " + textField.getText)
                timer = new Timer(waitTime, listener)
                timer.setRepeats(false)
                timer.setCoalesce(true)
                timer.start
                theTextFieldSeemsToBeChanging = true
            }
        }
        def changedUpdate(e: DocumentEvent) {}
        def removeUpdate(e: DocumentEvent) {}
    })


    def getMainFrame = mainFrame
    def updateUIBasedOnStates {}
    
    def updateUISpeakingHasEnded {
        textField.setText("")
    }
    
//    def getTime = System.currentTimeMillis
  
}









