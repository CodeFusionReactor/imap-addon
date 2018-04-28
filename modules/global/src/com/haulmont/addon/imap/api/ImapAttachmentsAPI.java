package com.haulmont.addon.imap.api;

import com.haulmont.addon.imap.entity.ImapMessageAttachment;

import java.io.InputStream;

/**
 * Provide operations to load IMAP message attachments
 */
public interface ImapAttachmentsAPI {
    String NAME = "imap_AttachmentsAPI";

    /**
     * Return an input stream to load a message attachment content
     * @param attachment            IMAP message attachment reference object
     * @return                      input stream, must be closed after use
     */
    InputStream openStream(ImapMessageAttachment attachment);

    /**
     * Load a file message attachment content into byte array.
     * @param attachment            IMAP message attachment reference object
     * @return                      attachment content
     */
    byte[] loadFile(ImapMessageAttachment attachment);
}
