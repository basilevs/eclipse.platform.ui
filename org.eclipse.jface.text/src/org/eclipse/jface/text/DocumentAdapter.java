/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jface.text;

 
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TextChangeListener;
import org.eclipse.swt.custom.TextChangedEvent;
import org.eclipse.swt.custom.TextChangingEvent;


/**
 * Adapts an <code>IDocument</code> to the <code>StyledTextContent</code> interface.
 */
class DocumentAdapter implements IDocumentAdapter, IDocumentListener, IDocumentAdapterExtension {

	/** The adapted document. */
	private IDocument fDocument;
	/** The registered text change listeners */
	private List fTextChangeListeners= new ArrayList(1);
	/** 
	 * The remembered document event
	 * @since 2.0
	 */
	private DocumentEvent fEvent;
	/** The line delimiter */
	private String fLineDelimiter= null;
	/** 
	 * Indicates whether this adapter is forwarding document changes
	 * @since 2.0
	 */
	private boolean fIsForwarding= true;
	/**
	 * Length of document at receipt of <code>documentAboutToBeChanged</code>
	 * @since 2.1
	 */
	private int fRememberedLengthOfDocument;
	/**
	 * Length of first document line at receipt of <code>documentAboutToBeChanged</code>
	 * @since 2.1
	 */
	private int fRememberedLengthOfFirstLine;
	/**
	 * The data of the event at receipt of <code>documentAboutToBeChanged</code>
	 * @since 2.1
	 */
	private  DocumentEvent fOriginalEvent= new DocumentEvent();
	
	
	/**
	 * Creates a new document adapter which is initiallly not connected to
	 * any document.
	 */
	public DocumentAdapter() {
	}
	
	/**
	 * Sets the given document as the document to be adapted.
	 *
	 * @param document the document to be adapted or <code>null</code> if there is no document
	 */
	public void setDocument(IDocument document) {
		
		if (fDocument != null)
			fDocument.removePrenotifiedDocumentListener(this);
		
		fDocument= document;
		fLineDelimiter= null;
		
		if (fDocument != null)
			fDocument.addPrenotifiedDocumentListener(this);
	}
	
	/*
	 * @see StyledTextContent#addTextChangeListener(TextChangeListener)
	 */
	public void addTextChangeListener(TextChangeListener listener) {
		Assert.isNotNull(listener);
		if (! fTextChangeListeners.contains(listener))
			fTextChangeListeners.add(listener);
	}
		
	/*
	 * @see StyledTextContent#removeTextChangeListener(TextChangeListener)
	 */
	public void removeTextChangeListener(TextChangeListener listener) {
		Assert.isNotNull(listener);
		fTextChangeListeners.remove(listener);
	}
	
	/*
	 * @see StyledTextContent#getLine(int)
	 */
	public String getLine(int line) {
		try {
			IRegion r= fDocument.getLineInformation(line);
			return fDocument.get(r.getOffset(), r.getLength());
		} catch (BadLocationException x) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			return null;
		}
	}
	
	/*
	 * @see StyledTextContent#getLineAtOffset(int)
	 */
	public int getLineAtOffset(int offset) {
		try {
			return fDocument.getLineOfOffset(offset);
		} catch (BadLocationException x) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			return -1;
		}
	}
	
	/*
	 * @see StyledTextContent#getLineCount()
	 */
	public int getLineCount() {
		return fDocument.getNumberOfLines();
	}
	
	/*
	 * @see StyledTextContent#getOffsetAtLine(int)
	 */
	public int getOffsetAtLine(int line) {
		try {
			return fDocument.getLineOffset(line);
		} catch (BadLocationException x) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			return -1;
		}
	}
	
	/*
	 * @see StyledTextContent#getTextRange(int, int)
	 */
	public String getTextRange(int offset, int length) {
		try {
			return fDocument.get(offset, length);
		} catch (BadLocationException x) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
			return null;
		}
	}
	
	/*
	 * @see StyledTextContent#replaceTextRange(int, int, String)
	 */
	public void replaceTextRange(int pos, int length, String text) {
		try {
			fDocument.replace(pos, length, text); 			
		} catch (BadLocationException x) {
			SWT.error(SWT.ERROR_INVALID_ARGUMENT);
		}
	}
	
	/*
	 * @see StyledTextContent#setText(String)
	 */
	public void setText(String text) {
		fDocument.set(text);
	}
	
	/*
	 * @see StyledTextContent#getCharCount()
	 */
	public int getCharCount() {
		return fDocument.getLength();
	}
	
	/*
	 * @see StyledTextContent#getLineDelimiter()
	 */
	public String getLineDelimiter() {
		
		if (fLineDelimiter == null) {
			
			try {
				fLineDelimiter= fDocument.getLineDelimiter(0);
			} catch (BadLocationException x) {
			}
			
			if (fLineDelimiter == null) {
				/*
				 * Follow up fix for: 1GF5UU0: ITPJUI:WIN2000 - "Organize Imports" in java editor inserts lines in wrong format
				 * The line delimiter must always be a legal document line delimiter.
				 */
				String sysLineDelimiter= System.getProperty("line.separator"); //$NON-NLS-1$
				String[] delimiters= fDocument.getLegalLineDelimiters();
				Assert.isTrue(delimiters.length > 0);
				for (int i= 0; i < delimiters.length; i++) {
					if (delimiters[i].equals(sysLineDelimiter)) {
						fLineDelimiter= sysLineDelimiter;
						break;
					}
				}
				
				if (fLineDelimiter == null) {
					// system line delimiter is not a legal document line delimiter
					fLineDelimiter= delimiters[0];
				}
			}
		}
		
		return fLineDelimiter;
	}
	
	/*
	 * @see IDocumentListener#documentChanged(DocumentEvent)
	 */
	public void documentChanged(DocumentEvent event) {
		// check whether the given event is the one which was remembered
		if (fEvent == null || event != fEvent)
			return;
			
		if (isPatchedEvent(event) || (event.getOffset() == 0 && event.getLength() == fRememberedLengthOfDocument)) {
			fLineDelimiter= null;
			fireTextSet();
		} else {
			if (event.getOffset() < fRememberedLengthOfFirstLine)
				fLineDelimiter= null;
			fireTextChanged();			
		}
	}
	
	/*
	 * @see IDocumentListener#documentAboutToBeChanged(DocumentEvent)
	 */
	public void documentAboutToBeChanged(DocumentEvent event) {
		
		fRememberedLengthOfDocument= fDocument.getLength();
		try {
			fRememberedLengthOfFirstLine= fDocument.getLineLength(0);
		} catch (BadLocationException e) {
			fRememberedLengthOfFirstLine= -1;
		}
		
		fEvent= event;
		rememberEventData(fEvent);
		fireTextChanging();
	}
	
	/**
	 * Checks whether this event has been changed between <code>documentAboutToBeChanged</code> and
	 * <code>documentChanged</code>.
	 * 
	 * @param event the event to be checked
	 * @return <code>true</code> if the event has been changed, <code>false</code> otherwise
	 */
	private boolean isPatchedEvent(DocumentEvent event) {
		return fOriginalEvent.fOffset != event.fOffset || fOriginalEvent.fLength != event.fLength || fOriginalEvent.fText != event.fText;
	}
	
	/**
	 * Makes a copy of the given event and remembers it.
	 * 
	 * @param event the event to be copied
	 */
	private void rememberEventData(DocumentEvent event) {
		fOriginalEvent.fOffset= event.fOffset;
		fOriginalEvent.fLength= event.fLength;
		fOriginalEvent.fText= event.fText;
	}
	
	/**
	 * Sends a text changed event to all registered listeners.
	 */
	private void fireTextChanged() {
		
		if (!fIsForwarding)
			return;
			
		TextChangedEvent event= new TextChangedEvent(this);
				
		if (fTextChangeListeners != null && fTextChangeListeners.size() > 0) {
			Iterator e= new ArrayList(fTextChangeListeners).iterator();
			while (e.hasNext())
				((TextChangeListener) e.next()).textChanged(event);
		}
	}
	
	/**
	 * Sends a text set event to all registered listeners.
	 */
	private void fireTextSet() {
		
		if (!fIsForwarding)
			return;
			
		TextChangedEvent event = new TextChangedEvent(this);
		
		if (fTextChangeListeners != null && fTextChangeListeners.size() > 0) {
			Iterator e= new ArrayList(fTextChangeListeners).iterator();
			while (e.hasNext())
				((TextChangeListener) e.next()).textSet(event);
		}
	}
	
	/**
	 * Sends the text changing event to all registered listeners.
	 */
	private void fireTextChanging() {    
		
		if (!fIsForwarding)
			return;
			
		try {
		    IDocument document= fEvent.getDocument();
		    if (document == null)
		    	return;

			TextChangingEvent event= new TextChangingEvent(this);
			event.start= fEvent.fOffset;
			event.replaceCharCount= fEvent.fLength;
			event.replaceLineCount= document.getNumberOfLines(fEvent.fOffset, fEvent.fLength) - 1;
			event.newText= fEvent.fText;
			event.newCharCount= (fEvent.fText == null ? 0 : fEvent.fText.length());
			event.newLineCount= (fEvent.fText == null ? 0 : document.computeNumberOfLines(fEvent.fText));
			
			if (fTextChangeListeners != null && fTextChangeListeners.size() > 0) {
				Iterator e= new ArrayList(fTextChangeListeners).iterator();
				while (e.hasNext())
					 ((TextChangeListener) e.next()).textChanging(event);
			}

		} catch (BadLocationException e) {
		}
	}
	
	/*
	 * @see IDocumentAdapterExtension#resumeForwardingDocumentChanges()
	 * @since 2.0
	 */
	public void resumeForwardingDocumentChanges() {
		fIsForwarding= true;
		fireTextSet();
	}
	
	/*
	 * @see IDocumentAdapterExtension#stopForwardingDocumentChanges()
	 * @since 2.0
	 */
	public void stopForwardingDocumentChanges() {
		fIsForwarding= false;
	}
}
