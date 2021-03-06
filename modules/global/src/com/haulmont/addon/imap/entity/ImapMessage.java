package com.haulmont.addon.imap.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haulmont.addon.imap.api.ImapFlag;
import com.haulmont.chile.core.annotations.NamePattern;
import com.haulmont.cuba.core.entity.StandardEntity;
import com.haulmont.cuba.core.entity.annotation.OnDeleteInverse;
import com.haulmont.cuba.core.global.DeletePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Flags;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NamePattern("%s (#%d) | caption, msgNum")
@Table(name = "IMAP_MESSAGE")
@Entity(name = "imap$Message")
public class ImapMessage extends StandardEntity {
    private final static Logger log = LoggerFactory.getLogger(ImapMessage.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final long serialVersionUID = -295396787486211720L;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FOLDER_ID")
    private ImapFolder folder;

    @Lob
    @Column(name = "FLAGS")
    private String flags;

    @NotNull
    @Column(name = "IS_ATL", nullable = false)
    private Boolean attachmentsLoaded = false;

    @NotNull
    @Column(name = "MSG_UID", nullable = false)
    private Long msgUid;

    @NotNull
    @Column(name = "MSG_NUM", nullable = false)
    private Integer msgNum;

    @Column(name = "THREAD_ID")
    private Long threadId;

    @Column(name = "REFERENCE_ID")
    private String referenceId;

    @Column(name = "MESSAGE_ID")
    private String messageId;

    @NotNull
    @Column(name = "CAPTION", nullable = false)
    private String caption;

    @Transient
    private List<ImapFlag> internalFlags = Collections.emptyList();

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }


    public String getFlags() {
        return flags;
    }

    void setFlags(String flags) {
        this.flags = flags;
    }

    public void setImapFlags(Flags flags) {
        Flags.Flag[] systemFlags = flags.getSystemFlags();
        String[] userFlags = flags.getUserFlags();
        List<ImapFlag> internalFlags = new ArrayList<>(systemFlags.length + userFlags.length);
        for (Flags.Flag systemFlag : systemFlags) {
            internalFlags.add(new ImapFlag(ImapFlag.SystemFlag.valueOf(systemFlag)));
        }
        for (String userFlag : userFlags) {
            internalFlags.add(new ImapFlag(userFlag));
        }
        try {
            if (!internalFlags.equals(this.internalFlags)) {
                log.debug("Convert imap flags {} to raw string", internalFlags);
                this.flags = OBJECT_MAPPER.writeValueAsString(internalFlags);
                this.internalFlags = internalFlags;
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Can't convert flags " + internalFlags, e);
        }
    }

    public Flags getImapFlags() {
        try {
            log.debug("Parse imap flags from raw string {}", flags);
            this.internalFlags = OBJECT_MAPPER.readValue(this.flags, new TypeReference<List<ImapFlag>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Can't parse flags from string " + flags, e);
        }

        Flags flags = new Flags();
        for (ImapFlag internalFlag : this.internalFlags) {
            flags.add(internalFlag.imapFlags());
        }
        return flags;
    }

    public void setAttachmentsLoaded(Boolean attachmentsLoaded) {
        this.attachmentsLoaded = attachmentsLoaded;
    }

    public Boolean getAttachmentsLoaded() {
        return attachmentsLoaded;
    }

    public ImapFolder getFolder() {
        return folder;
    }

    public void setFolder(ImapFolder folder) {
        this.folder = folder;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public void setThreadId(Long threadId) {
        this.threadId = threadId;
    }

    public Long getThreadId() {
        return threadId;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getCaption() {
        return caption;
    }

    public void setMsgUid(Long msgUid) {
        this.msgUid = msgUid;
    }

    public Long getMsgUid() {
        return msgUid;
    }

    public Integer getMsgNum() {
        return msgNum;
    }

    public void setMsgNum(Integer msgNum) {
        this.msgNum = msgNum;
    }
}