/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ws.wsrm;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import javax.xml.namespace.QName;
import jakarta.xml.soap.Node;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;

import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;

/**
 * @author <a href="mailto:rsvoboda@redhat.com">Rostislav Svoboda</a>
 */
public class ReliableCheckHandler implements SOAPHandler<SOAPMessageContext> {

    /*
     * 1 -- Body - CreateSequence
     * 2 -- Body - CreateSequenceResponse
     * 3 -- Header - wsrm:Sequence
     * 4 -- Header - wsrm:SequenceAcknowledgement
     */
    int status = 0;
    private static Logger log = Logger.getLogger(ReliableCheckHandler.class);

    public Set<QName> getHeaders() {
        return null;
    }

    public boolean handleFault(SOAPMessageContext context) {
        handleContext(context);
        return true;
    }

    public boolean handleMessage(SOAPMessageContext context) {
        handleContext(context);
        return true;
    }

    private synchronized void handleContext(SOAPMessageContext smc) {
        Boolean outboundProperty = (Boolean) smc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        SOAPMessage message = smc.getMessage();

        if (outboundProperty.booleanValue()) {
            log.debug("Outgoing message:");
        } else {
            log.debug("Incoming message:");
        }

        log.debug("-----------");
        try {
            JBossLoggingOutputStream os = new JBossLoggingOutputStream(log, Level.DEBUG);
            message.writeTo(os);
            os.flush();
            log.debug("");
        } catch (Exception e) {
            log.debug("Exception in handler: " + e);
        }
        log.debug("-----------");

        SOAPElement firstBodyElement;
        try {
            switch (status % 4) {
                case 0:
                    @SuppressWarnings("unchecked")
                    Iterator<Node> it = (Iterator<Node>) message.getSOAPBody().getChildElements();
                    if (it.hasNext()) {
                        firstBodyElement = (SOAPElement) it.next();
                        final QName createSequenceQName = new QName("http://schemas.xmlsoap.org/ws/2005/02/rm", "CreateSequence");
                        if (!createSequenceQName.equals(firstBodyElement.getElementQName())) {
                            throw new WebServiceException("CreateSequence in soap body was expected, but it contains '"
                                    + firstBodyElement.getElementQName() + "'");
                        }
                        status++;
                    } else {
                        //we could get multiple acknowledments
                        verifySequenceAcknowledgement(message);
                    }
                    break;
                case 1:
                    firstBodyElement = (SOAPElement) message.getSOAPBody().getChildElements().next();
                    final QName createSequenceResponseQName = new QName("http://schemas.xmlsoap.org/ws/2005/02/rm", "CreateSequenceResponse");
                    if (!createSequenceResponseQName.equals(firstBodyElement.getElementQName())) {
                        throw new WebServiceException("CreateSequenceResponse in soap body was expected, but it contains '"
                                + firstBodyElement.getElementQName() + "'");
                    }
                    status++;
                    break;
                case 2:
                    Iterator headerElements = message.getSOAPHeader().getChildElements();
                    boolean found = false;
                    final QName sequenceQName = new QName("http://schemas.xmlsoap.org/ws/2005/02/rm", "Sequence");
                    while (headerElements.hasNext()) {
                        SOAPElement soapElement = (SOAPElement) headerElements.next();
                        if (sequenceQName.equals(soapElement.getElementQName())) {
                            found = true;
                        }
                    }
                    if (!found) {
                        throw new WebServiceException("wsrm:Sequence is not present in soap header");
                    }
                    status++;
                    break;
                case 3:
                    if (verifySequenceAcknowledgement(message)) {
                        status++;
                    }
                    break;
            }
        } catch (SOAPException ex) {
            throw new WebServiceException(ex.getMessage(), ex);
        }
    }

    private boolean verifySequenceAcknowledgement(SOAPMessage message) throws SOAPException {
        Iterator headerElements = message.getSOAPHeader().getChildElements();
        boolean found = false;
        boolean otherRMHeadersFound = false;
        final QName sequenceAckQName = new QName("http://schemas.xmlsoap.org/ws/2005/02/rm", "SequenceAcknowledgement");
        while (headerElements.hasNext()) {
            SOAPElement soapElement = (SOAPElement) headerElements.next();
            if (sequenceAckQName.equals(soapElement.getElementQName())) {
                found = true;
            } else if ("http://schemas.xmlsoap.org/ws/2005/02/rm".equals(soapElement.getNamespaceURI())) {
                otherRMHeadersFound = true;
            }
        }
        //fail if we did not find the sequence ack and the message has other WS-RM headers (hence out of order) or has body contents (hence a non-reliable message is being processed)
        if (!found && (otherRMHeadersFound || message.getSOAPBody().getChildElements().hasNext())) {
            throw new WebServiceException("wsrm:SequenceAcknowledgement is not present in soap header");
        }
        return found;
    }

    public void close(MessageContext context) {
    }

    private class JBossLoggingOutputStream extends OutputStream {

        public static final int DEFAULT_BUFFER_LENGTH = 2048;

        protected boolean hasBeenClosed = false;
        protected byte[] buf;
        protected int count;
        private int bufLength;
        protected Logger logger;
        protected Level level;

        JBossLoggingOutputStream(Logger log, Level level) throws IllegalArgumentException {
            if (log == null) {
                throw new IllegalArgumentException("Null category!");
            }
            if (level == null) {
                throw new IllegalArgumentException("Null priority!");
            }
            this.level = level;
            logger = log;
            bufLength = DEFAULT_BUFFER_LENGTH;
            buf = new byte[DEFAULT_BUFFER_LENGTH];
            count = 0;
        }

        public void close() {
            flush();
            hasBeenClosed = true;
        }

        public void write(final int b) throws IOException {
            if (hasBeenClosed) {
                throw new IOException("The stream has been closed.");
            }
            // would this be writing past the buffer?
            if (count == bufLength) {
                // grow the buffer
                final int newBufLength = bufLength + DEFAULT_BUFFER_LENGTH;
                final byte[] newBuf = new byte[newBufLength];
                System.arraycopy(buf, 0, newBuf, 0, bufLength);
                buf = newBuf;
                bufLength = newBufLength;
            }
            buf[count] = (byte) b;
            count++;
        }

        public void flush() {
            if (count == 0) {
                return;
            }
            // don't print out blank lines; flushing from PrintStream puts
            // out these
            // For linux system
            if (count == 1 && ((char) buf[0]) == '\n') {
                reset();
                return;
            }
            // For mac system
            if (count == 1 && ((char) buf[0]) == '\r') {
                reset();
                return;
            }
            // On windows system
            if (count == 2 && (char) buf[0] == '\r' && (char) buf[1] == '\n') {
                reset();
                return;
            }
            final byte[] theBytes = new byte[count];
            System.arraycopy(buf, 0, theBytes, 0, count);
            logger.log(level, new String(theBytes, StandardCharsets.UTF_8));
            reset();
        }

        private void reset() {
            // not resetting the buffer -- assuming that if it grew then it
            // will likely grow similarly again
            count = 0;
        }
    }
}
