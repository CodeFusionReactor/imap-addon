package com.haulmont.components.imap.web.imapmailbox;

import com.google.common.collect.Lists;
import com.haulmont.components.imap.dto.ImapFolderDto;
import com.haulmont.components.imap.entity.ImapFolderEvent;
import com.haulmont.components.imap.entity.ImapMailBox;
import com.haulmont.components.imap.entity.ImapFolder;
import com.haulmont.components.imap.entity.ImapEventType;
import com.haulmont.components.imap.web.ds.ImapFolderDatasource;
import com.haulmont.bali.util.ParamsMap;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.components.AbstractEditor;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ImapMailBoxFolders extends AbstractEditor<ImapMailBox> {

    @Inject
    private ImapFolderDatasource imapFolderDs;

    @Inject
    private Metadata metadata;

    @Override
    public void init(Map<String, Object> params) {
        imapFolderDs.refresh(ParamsMap.of(ImapFolderDatasource.FOLDER_DS_MAILBOX_PARAM, params.get("mailBox")));
    }

    @Override
    protected void postInit() {
        ImapMailBox mailBox = getItem();

        addCloseWithCommitListener(() -> {
            Map<String, ImapFolderDto> selected = new HashMap<>(imapFolderDs.getItems().stream()
                    .filter(ImapFolderDto::getSelected)
                    .collect(Collectors.toMap(ImapFolderDto::getFullName, Function.identity()))
            );

            List<ImapFolder> mailBoxFolders = mailBox.getFolders();
            if (mailBoxFolders == null) {
                mailBoxFolders = new ArrayList<>();
                mailBox.setFolders(mailBoxFolders);
            }
            List<ImapFolder> toDelete = new ArrayList<>(mailBoxFolders.size());
            for (ImapFolder folder : mailBoxFolders) {
                String fullName = folder.getName();
                if (!selected.containsKey(fullName)) {
                    toDelete.add(folder);
                } else {
                    selected.remove(fullName);
                }
            }


            mailBox.getFolders().addAll(selected.values().stream().map(dto -> {
                ImapFolder imapFolder = metadata.create(ImapFolder.class);
                        imapFolder.setMailBox(mailBox);
                        imapFolder.setName(dto.getFullName());

                        ImapFolderEvent newEmailEvent = metadata.create(ImapFolderEvent.class);
                        newEmailEvent.setEvent(ImapEventType.NEW_EMAIL);
                        newEmailEvent.setFolder(imapFolder);

                        imapFolder.setEvents(Lists.newArrayList(newEmailEvent));
                        return imapFolder;
                    }).collect(Collectors.toList())
            );

            mailBox.getFolders().removeAll(toDelete);
        });
    }

}