/*
 *  Kontalk Java client
 *  Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.client;

import java.util.Date;
import java.util.logging.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.Base64;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.kontalk.model.MessageContent;
import org.kontalk.model.MessageContent.Attachment;
import org.kontalk.model.MessageList;

/**
 *
 * @author Alexander Bikadorov <abiku@cs.tu-berlin.de>
 */
final public class KonMessageListener implements PacketListener {
    private final static Logger LOGGER = Logger.getLogger(KonMessageListener.class.getName());

    private final Client mClient;

    KonMessageListener(Client client) {
        mClient = client;

        ProviderManager.addExtensionProvider(SentServerReceipt.ELEMENT_NAME, SentServerReceipt.NAMESPACE, new SentServerReceipt.Provider());
        ProviderManager.addExtensionProvider(ReceivedServerReceipt.ELEMENT_NAME, ReceivedServerReceipt.NAMESPACE, new ReceivedServerReceipt.Provider());
        ProviderManager.addExtensionProvider(ServerReceiptRequest.ELEMENT_NAME, ServerReceiptRequest.NAMESPACE, new ServerReceiptRequest.Provider());
        ProviderManager.addExtensionProvider(AckServerReceipt.ELEMENT_NAME, AckServerReceipt.NAMESPACE, new AckServerReceipt.Provider());
        ProviderManager.addExtensionProvider(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE, new OutOfBandData.Provider());
        //ProviderManager.addExtensionProvider(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE, new BitsOfBinary.Provider());
        ProviderManager.addExtensionProvider(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE, new E2EEncryption.Provider());
    }

    // TODO synchronize!?
    @Override
    public void processPacket(Packet packet) {
        org.jivesoftware.smack.packet.Message m = (org.jivesoftware.smack.packet.Message) packet;
        if (m.getType() == org.jivesoftware.smack.packet.Message.Type.chat) {
            // somebody has news for us
            processChatMessage(m);
        }

        // error message
        else if (m.getType() == org.jivesoftware.smack.packet.Message.Type.error) {
            LOGGER.warning("got error message: "+m);
        } else {
            LOGGER.warning("unknown message type: "+m.getType());
        }
    }

    private void processChatMessage(Message m) {
        LOGGER.info("got message: "+m.toXML());
        // note: thread and subject are null if message comes from Kontalk
        // android client

        // timestamp
        // delayed deliver extension is the first the be processed
        // because it's used also in delivery receipts
        // first: new XEP-0203 specification
        PacketExtension delay = m.getExtension("delay", "urn:xmpp:delay");
        // fallback: obsolete XEP-0091 specification
        if (delay == null) {
            delay = m.getExtension("x", "jabber:x:delay");
        }
        Date date;
        if (delay != null && delay instanceof DelayInformation) {
                date = ((DelayInformation) delay).getStamp();
                // TODO if date is in future set it to 'now'
        } else {
            // apparently there was no delay, so use the current time
            date = new Date();
        }

        // process possible chat state notification (XEP-0085)
        PacketExtension chatstate = m.getExtension("http://jabber.org/protocol/chatstates");
        if (chatstate != null) {
            LOGGER.info("got chatstate: " + chatstate.getElementName());
            // TODO the thread needs to be imformed
            // thread.processChatState(user, chatStateValue);
            if (!chatstate.getElementName().equals(ChatState.active.name()))
                return;
        }

        // delivery receipt
        PacketExtension receiptExt = m.getExtension(ServerReceipt.NAMESPACE);
        if (receiptExt != null && receiptExt instanceof ServerReceipt) {
            ServerReceipt serverReceipt = (ServerReceipt) receiptExt;
            processReceipt(m, serverReceipt);
            return;
        }

        // must be an incoming message

        // get content/text from body and/or encryption/url extension
        MessageContent content = parseMessageContent(m);

        // make sure not to save a message without content
        if (content.isEmpty()) {
            LOGGER.warning("can't find any content in message");
            return;
        }

        // receipt id
        String receiptID = "";
        if (receiptExt != null && receiptExt instanceof ServerReceiptRequest) {
            ServerReceiptRequest req = (ServerReceiptRequest) receiptExt;
            receiptID = req.getId() == null ? "" : req.getId();
        }

        String xmppID = m.getPacketID() != null ? m.getPacketID() : "";
        if (xmppID.isEmpty()) {
            LOGGER.warning("message does not have a XMPP ID");
        }

        // add message
        MessageList.getInstance().addFrom(m.getFrom(),
                xmppID,
                m.getThread(),
                date,
                receiptID,
                content);

        // send a 'received' for a request
        if (!receiptID.isEmpty()) {
            Message received = new Message(m.getFrom(), Message.Type.chat);
            received.addExtension(new ReceivedServerReceipt(receiptID));
            mClient.sendPacket(received);
        }
    }

    private void processReceipt(Message m, ServerReceipt receipt) {
        if (receipt instanceof SentServerReceipt) {
            SentServerReceipt sentServerReceipt = (SentServerReceipt) receipt;
            // update message status and save receipt ID
            MessageList.getInstance().updateMsgBySentReceipt(m.getPacketID(),
                    sentServerReceipt.getId());
            return;
        }
        if (receipt instanceof ReceivedServerReceipt) {
            ReceivedServerReceipt receivedServerReceipt = (ReceivedServerReceipt) receipt;
            // HOORAY! our message was received
            MessageList.getInstance().updateMsgByReceivedReceipt(
                    receivedServerReceipt.getId());
            // send acknowledgment
            Message ack = new Message(m.getFrom(), Message.Type.chat);
            ack.addExtension(new AckServerReceipt(m.getPacketID()));
            mClient.sendPacket(ack);
            return;
        }
        if (receipt instanceof AckServerReceipt) {
            //AckServerReceipt ackServerReceipt = (AckServerReceipt) receipt;
            // TODO it looks like the packet id is used now to identify the
            // 'received' for this acknowledement, unlike the spec says
            // ignore this for now
            // update: actually we don't have to do anything here
            return;
        }
        LOGGER.warning("unknown server receipt: " + receipt.toXML());
    }

    public static MessageContent parseMessageContent(Message m) {
        // default body
        String plainText = m.getBody() != null ? m.getBody() : "";

        // encryption extension, decrypted later
        String encryptedContent = "";
        PacketExtension encryptionExt = m.getExtension("e2e", "urn:ietf:params:xml:ns:xmpp-e2e");
        if (encryptionExt != null && encryptionExt instanceof E2EEncryption) {
            if (m.getBody() != null)
                LOGGER.warning("message contains encryption and body (ignoring body)");
            E2EEncryption encryption = (E2EEncryption) encryptionExt;
            encryptedContent = Base64.encodeBytes(encryption.getData());
        }

        // Out of Band Data: a URI to a file
        Attachment attachment = null;
        PacketExtension oobExt = m.getExtension("x", "jabber:x:oob");
        if (oobExt!= null && oobExt instanceof OutOfBandData) {
            LOGGER.info("Parsing Out of Band Data");
            OutOfBandData oobData = (OutOfBandData) oobExt;
            attachment = new MessageContent.Attachment(oobData.getUrl(),
                    oobData.getMime() != null ? oobData.getMime() : "",
                    oobData.getLength(),
                    oobData.isEncrypted());
        }
        return new MessageContent(plainText, attachment, encryptedContent);
    }

}