package com.haulmon.components.imap.entity;

import com.haulmont.chile.core.datatypes.impl.EnumClass;

import javax.annotation.Nullable;


public enum MailEventType implements EnumClass<String> {

    NEW_EMAIL("new_email"),
    EMAIL_SEEN("seen"),
    NEW_ANSWER("new_answer"),
    EMAIL_MOVED("moved"),
    FLAGS_UPDATED("flags_updated"),
    EMAIL_DELETED("deleted"),
    NEW_THREAD("new_thread");

    private String id;

    MailEventType(String value) {
        this.id = value;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public static MailEventType fromId(String id) {
        for (MailEventType at : MailEventType.values()) {
            if (at.getId().equals(id)) {
                return at;
            }
        }
        return null;
    }
}