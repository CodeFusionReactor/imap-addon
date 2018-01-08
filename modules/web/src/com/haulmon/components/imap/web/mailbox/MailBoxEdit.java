package com.haulmon.components.imap.web.mailbox;

import com.haulmon.components.imap.entity.MailAuthenticationMethod;
import com.haulmon.components.imap.entity.MailFolder;
import com.haulmon.components.imap.entity.MailSimpleAuthentication;
import com.haulmon.components.imap.service.ImapService;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.gui.components.AbstractEditor;
import com.haulmon.components.imap.entity.MailBox;
import com.haulmont.cuba.gui.components.FieldGroup;

import javax.inject.Inject;
import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MailBoxEdit extends AbstractEditor<MailBox> {

    @Inject
    private FieldGroup mainParams;

    @Inject
    private ImapService service;

    @Inject
    private Metadata metadata;

    @Override
    protected void initNewItem(MailBox item) {
        item.setAuthenticationMethod(MailAuthenticationMethod.SIMPLE);
        item.setPollInterval(10 * 60);
        item.setAuthentication(metadata.create(MailSimpleAuthentication.class));
    }

    @Override
    protected void postInit() {
        FieldGroup.FieldConfig mailBoxRootCertificateField = this.mainParams.getFieldNN("mailBoxRootCertificateField");
        mailBoxRootCertificateField.setVisible(getItem().getSecureMode() != null);
    }

    @Override
    protected boolean preCommit() {
        MailBox mailBox = getItem();
        try {
            List<String> folders = service.fetchFolders(mailBox);
            List<MailFolder> boxFolders = mailBox.getFolders();
            if (boxFolders == null) {
                mailBox.setFolders(new ArrayList<>(folders.size()));
            }
            List<String> savedFolders = mailBox.getFolders().stream()
                    .map(MailFolder::getName)
                    .collect(Collectors.toList());

            folders.stream().filter(f -> !savedFolders.contains(f)).forEach(name -> {
                MailFolder mailFolder = metadata.create(MailFolder.class);
                mailFolder.setMailBox(mailBox);
                mailFolder.setName(name);
                mailBox.getFolders().add(mailFolder);
                getDsContext().addBeforeCommitListener(context -> context.getCommitInstances().add(mailFolder));
            });

            return true;
        } catch (MessagingException e) {
            return false;
        }
    }
}