package com.haulmont.addon.imap.core;

import com.haulmont.addon.imap.core.ext.ThreadExtension;
import com.haulmont.addon.imap.dto.ImapFolderDto;
import com.haulmont.addon.imap.entity.ImapFolder;
import com.haulmont.addon.imap.entity.ImapMailBox;
import com.haulmont.addon.imap.entity.ImapMessage;
import com.haulmont.addon.imap.events.BaseImapEvent;
import com.haulmont.addon.imap.events.EmailAnsweredImapEvent;
import com.haulmont.cuba.core.global.Metadata;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.*;
import javax.mail.internet.MimeUtility;
import javax.mail.search.SearchTerm;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

@Component("imap_ImapOperations")
public class ImapOperations {
    private final static Logger log = LoggerFactory.getLogger(ImapOperations.class);

    private static final String REFERENCES_HEADER = "References";
    private static final String IN_REPLY_TO_HEADER = "In-Reply-To";
    private static final String SUBJECT_HEADER = "Subject";
    public static final String MESSAGE_ID_HEADER = "Message-ID";

    private final ImapHelper imapHelper;
    private final Metadata metadata;

    @Autowired
    public ImapOperations(ImapHelper imapHelper, Metadata metadata) {
        this.imapHelper = imapHelper;
        this.metadata = metadata;
    }

    public List<ImapFolderDto> fetchFolders(IMAPStore store) throws MessagingException {
        List<ImapFolderDto> result = new ArrayList<>();

        Folder defaultFolder = store.getDefaultFolder();

        Folder[] allFolders = defaultFolder.list("*");

        List<String> sortedFolderNames = Arrays.stream(allFolders)
                .map(Folder::getFullName)
                .sorted()
                .collect(Collectors.toList());
        Map<String, ImapFolderDto> foldersByFullName = new HashMap<>();
        Folder[] folders = allFolders;
        while (folders.length > 0) {
            List<Folder> unprocessedFolders = new ArrayList<>();
            for (Folder folder : folders) {
                String fullName = folder.getFullName();
                int i = Collections.binarySearch(sortedFolderNames, fullName);
                String parentName = null;
                for (int j = i - 1; j >= 0; j--) {
                    if (fullName.startsWith(sortedFolderNames.get(j))) {
                        parentName = sortedFolderNames.get(j);
                        break;
                    }
                }
                if (parentName == null) {
                    ImapFolderDto dto = map((IMAPFolder) folder);
                    foldersByFullName.put(fullName, dto);
                    result.add(dto);
                } else {
                    ImapFolderDto parentDto = foldersByFullName.get(parentName);
                    if (parentDto != null) {
                        ImapFolderDto dto = map((IMAPFolder) folder);
                        foldersByFullName.put(fullName, dto);
                        parentDto.getChildren().add(dto);
                        dto.setParent(parentDto);
                    } else {
                        unprocessedFolders.add(folder);
                    }
                }
            }
            folders = unprocessedFolders.toArray(new Folder[0]);
        }
        result.sort(Comparator.comparing(ImapFolderDto::getFullName));

        return result;
    }

    private ImapFolderDto map(IMAPFolder folder) throws MessagingException {
        ImapFolderDto dto = metadata.create(ImapFolderDto.class);
        dto.setName(folder.getName());
        dto.setFullName(folder.getFullName());
        dto.setCanHoldMessages(ImapHelper.canHoldMessages(folder));
        dto.setChildren(new ArrayList<>());
        dto.setImapFolder(folder);

        return dto;

    }

    public List<IMAPMessage> search(IMAPFolder folder, SearchTerm searchTerm, ImapMailBox mailBox) throws MessagingException {
        log.debug("search messages in {} with {}", folder.getFullName(), searchTerm) ;

        Message[] messages = folder.search(searchTerm);
        return fetch(folder, mailBox, messages);
    }

    public List<IMAPMessage> searchMessageIds(IMAPFolder folder, SearchTerm searchTerm) throws MessagingException {
        log.debug("search messages in {} with {}", folder.getFullName(), searchTerm) ;

        Message[] messages = folder.search(searchTerm);
        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(MESSAGE_ID_HEADER);
        return fetch(folder, fetchProfile, messages);
    }

    public List<IMAPMessage> fetchUIDs(IMAPFolder folder, Message[] messages) throws MessagingException {
        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(UIDFolder.FetchProfileItem.UID);
        return fetch(folder, fetchProfile, messages);
    }

    public void setAnsweredFlag(ImapMailBox mailBox, Collection<BaseImapEvent> imapEvents) {
        Map<ImapFolder, List<ImapMessage>> answeredMessagesByFolder = imapEvents.stream()
                .filter(event -> event instanceof EmailAnsweredImapEvent)
                .map(BaseImapEvent::getMessage)
                .collect(Collectors.groupingBy(ImapMessage::getFolder));
        for (Map.Entry<ImapFolder, List<ImapMessage>> folderReplies : answeredMessagesByFolder.entrySet()) {
            imapHelper.doWithFolder(
                    mailBox,
                    folderReplies.getKey().getName(),
                    new Task<>("set answered flag", false, folder -> {
                        long[] messageUIDs = folderReplies.getValue().stream().mapToLong(ImapMessage::getMsgUid).toArray();
                        Message[] messages = folder.getMessagesByUID(messageUIDs);
                        folder.setFlags(
                                messages,
                                new Flags(Flags.Flag.ANSWERED),
                                true
                        );
                        return null;
                    })
            );
        }
    }

    public List<IMAPMessage> getAllByUIDs(IMAPFolder folder, long[] messageUIDs, ImapMailBox mailBox) throws MessagingException {
        if (log.isDebugEnabled()) {
            log.debug("get messages by messageUIDs {} in {}", Arrays.toString(messageUIDs), folder.getFullName());
        }

        Message[] messages = folder.getMessagesByUID(messageUIDs);
        return fetch(folder, mailBox, messages);
    }

    public String getRefId(IMAPMessage message) throws MessagingException {
        String refHeader = message.getHeader(REFERENCES_HEADER, null);
        if (refHeader == null) {
            refHeader = message.getHeader(IN_REPLY_TO_HEADER, null);
        } else {
            refHeader = refHeader.split("\\s+")[0];
        }
        if (refHeader != null && refHeader.length() > 0) {
            return refHeader;
        }

        return null;
    }

    public Long getThreadId(IMAPMessage message, ImapMailBox mailBox) throws MessagingException {
        if (!imapHelper.supportsCapability(mailBox, ThreadExtension.CAPABILITY_NAME)) {
            return null;
        }
        Object threadItem = message.getItem(ThreadExtension.FETCH_ITEM);
        return threadItem instanceof ThreadExtension.X_GM_THRID ? ((ThreadExtension.X_GM_THRID) threadItem).x_gm_thrid : null;
    }

    public String getSubject(IMAPMessage message) throws MessagingException {
        String subject = message.getHeader(SUBJECT_HEADER, null);
        if (subject != null && subject.length() > 0) {
            return decode(subject);
        } else {
            return "(No Subject)";
        }
    }

    private List<IMAPMessage> fetch(IMAPFolder folder, ImapMailBox mailBox, Message[] messages) throws MessagingException {
        return fetch(folder, headerProfile(mailBox), messages);
    }

    private List<IMAPMessage> fetch(IMAPFolder folder, FetchProfile fetchProfile, Message[] messages) throws MessagingException {
        Message[] nonNullMessages = Arrays.stream(messages).filter(Objects::nonNull).toArray(Message[]::new);
        folder.fetch(nonNullMessages, fetchProfile);
        List<IMAPMessage> result = new ArrayList<>(nonNullMessages.length);
        for (Message message : nonNullMessages) {
            result.add((IMAPMessage) message);
        }
        return result;
    }

    private FetchProfile headerProfile(ImapMailBox mailBox) throws MessagingException {
        FetchProfile profile = new FetchProfile();
        profile.add(FetchProfile.Item.FLAGS);
        profile.add(UIDFolder.FetchProfileItem.UID);
        profile.add(REFERENCES_HEADER);
        profile.add(IN_REPLY_TO_HEADER);
        profile.add(SUBJECT_HEADER);
        profile.add(MESSAGE_ID_HEADER);

        if (imapHelper.getStore(mailBox).hasCapability(ThreadExtension.CAPABILITY_NAME)) {
            profile.add(ThreadExtension.FetchProfileItem.X_GM_THRID);
        }

        return profile;
    }

    private String decode(String val) {
        try {
            return MimeUtility.decodeText(MimeUtility.unfold(val));
        } catch (UnsupportedEncodingException ex) {
            return val;
        }
    }
}
