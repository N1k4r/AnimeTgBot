package io.proj3ct.TelegramBot.service;

import io.proj3ct.TelegramBot.UserSubs;
import io.proj3ct.TelegramBot.config.BotConfig;
import org.imgscalr.Scalr;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import javax.imageio.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;


@Component
public class TgBot extends TelegramLongPollingBot {
    static final String URL_PREVIEW_PIC = "https://anime-pictures.net";
    static final String FINAL_MIDDLE_URL = "?if=ANIME-PICTURES.NET_-_";
    static final String FINAL_START_URL = "https://ip1.anime-pictures.net/direct-images/";
    static final String FINAL_DOWNLOAD_URL = "https://anime-pictures.net/pictures/download_image/";
    static Map<Long, UserSubs> subs = new HashMap<>();


    final BotConfig config;
    public TgBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "start work"));
        listOfCommands.add(new BotCommand("/subscriptions", "number of subscriptions"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageTxt = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            long messageId = update.getMessage().getMessageId();
            switch (messageTxt) {
                case "/start" -> sendMessage(chatId, "Write me the name of the anime or the name of the character)");
                case "/subscriptions" -> countOfSub(chatId, messageId);
                default -> searchResult(chatId, messageTxt.toUpperCase());
            }
        } else if (update.hasCallbackQuery()){
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            long  messageId = update.getCallbackQuery().getMessage().getMessageId();
            String lastMessage = update.getCallbackQuery().getMessage().getText();
            String  request = update.getCallbackQuery().getData();

            if (request.startsWith("Subscribe")){
                request = request.substring(9);
                if (!(subs.containsKey(chatId)))
                    subs.put(chatId, new UserSubs());
                if (subs.get(chatId).setSub(request, ""))
                    editMedia(chatId, messageId, "SubPic.jpg", inlineKeyboardMarkup(new String[]{request, "Unsubscribe"}));
                else
                    sendMessage(chatId, "You have already subscribed");
                return;
            }

            if (request.startsWith("Unsubscribe")) {
                request = request.substring(11);
                    if (subs.get(chatId).deleteSub(request) && update.getCallbackQuery().getMessage().hasPhoto())
                        editMedia(chatId, messageId, "UnsubPic.jpg", inlineKeyboardMarkup(new String[]{request, "Subscribe"}));
                    else
                        sendMessage(chatId, "You have already cancelled your subscription");
                return;
            }

            if (request.startsWith("Exit")){
                deleteMessage(chatId, messageId);
                return;
            }

            if (request.startsWith("View") || request.startsWith("Next")){
                String requestSub = subs.get(chatId).pollQueue();
                String[] buttons = new String[]{requestSub, "Next", "Unsubscribe", "Exit"};
                if (subs.get(chatId).emptyQueue())
                    buttons = new String[]{requestSub, "Unsubscribe", "Exit"};
                editMessageText(chatId, messageId, lastMessage, inlineKeyboardMarkup(buttons));
                return;
            }

            String url = downloadContent(request);
            String[] buttons = new String[]{request, url, "Subscribe"};
            if (subs.containsKey(chatId) && subs.get(chatId).keyExist(request))
                buttons = new String[]{request, url, "Unsubscribe"};

            if (lastMessage == null) {
                editMedia(chatId, messageId, url, inlineKeyboardMarkup(buttons));
                return;
                }
            editMessageText(chatId, messageId, "Loading...");
            sendPhoto(chatId, new InputFile(resizePic(url)), inlineKeyboardMarkup(buttons));
            deleteMessage(chatId, messageId);
        }
        else
            sendMessage(update.getMessage().getChatId(), "I dont know what to do with it");
    }

    private void sendMessage(long chatId, String textToSend, InlineKeyboardMarkup... markup) {
        SendMessage message = new SendMessage(String.valueOf(chatId), textToSend);
        if (markup.length > 0){
            message.setReplyMarkup(markup[0]);
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
        }
    }

    private void editMessageText(long chatId, long messageId, String text, InlineKeyboardMarkup... markup) {
        EditMessageText messageText = new EditMessageText();
        messageText.setText(text);
        messageText.setMessageId((int) messageId);
        messageText.setChatId(chatId);
        if (markup.length > 0)
            messageText.setReplyMarkup(markup[0]);
        try {
            execute(messageText);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void editMedia(long chatId, long messageId, String url, InlineKeyboardMarkup markup){
        EditMessageMedia media = new EditMessageMedia();
        media.setChatId(chatId);
        media.setMessageId((int) messageId);
        media.setReplyMarkup(markup);

        InputMediaPhoto media1 = new InputMediaPhoto();
        File file = url.startsWith("http") ? resizePic(url) : new File(url);
        media1.setMedia(file, "pic");
        media.setMedia(media1);
        try {
            execute(media);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendPhoto(long chatId, InputFile file, InlineKeyboardMarkup markup, String... caption) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setReplyMarkup(markup);

        if (caption.length > 0)
            sendPhoto.setCaption(caption[0]);
        try {
            sendPhoto.setPhoto(file);
            execute(sendPhoto);
        } catch (Exception e) {
        }
    }

    private void deleteMessage(long chatId, long messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setMessageId((int) messageId);
        deleteMessage.setChatId(chatId);
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void searchResult(long chatId, String request){
        try{
            StringBuilder UrlPreviewPic = new StringBuilder("https://anime-pictures.net/pictures/view_posts/0?search_tag=//&order_by=date&ldate=0&lang=en");
            Document doc = Jsoup.connect(String.valueOf(UrlPreviewPic.insert(60, request))).get();
            String post = doc.select("div.pagination.svelte-18faof").text();
            post = post.substring(0, post.indexOf("pic")).replaceAll("\\D", "");

            if (post.equals("0")){
                sendMessage(chatId, "Pictures not found");
                return;
            }
            sendMessage(chatId, "Search pictures: " + post, inlineKeyboardMarkup(new String[]{request}));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String downloadContent(String request){
        try{
            ArrayList<String> urlsPreviewPic = new ArrayList<>();
            ArrayList<String> urlsPreviewSrc = new ArrayList<>();

            StringBuilder UrlPreviewPic = new StringBuilder("https://anime-pictures.net/pictures/view_posts/0?search_tag=//&order_by=date&ldate=0&lang=en");
            request = String.valueOf(UrlPreviewPic.insert(60, request));

//Connect to request page
            Document doc = Jsoup.connect(request).get();
            int pages = 0;
            if ((doc.select("p.numeric_pages.svelte-ho8yi0").hasAttr("href"))){
                for (int i = 0; i < 2; i++)
                    doc.select("p.numeric_pages.svelte-ho8yi0 a").last().remove();
                pages = Integer.parseInt(doc.select("p.numeric_pages.svelte-ho8yi0 a").last().text());
            }

//Connect to random page
            int randomPages = (int)(Math.random() * (pages + 1));
            Document randomPageDoc = Jsoup.connect(request.replaceAll("posts/0", "posts/" +randomPages)).get();
            Elements posts = randomPageDoc.select("span.img_block2.img_block_big");
            posts.forEach(postUrl -> urlsPreviewPic.add(postUrl.select("a").attr("href")));
            posts.forEach(postSrc -> urlsPreviewSrc.add(postSrc.select("img").attr("src")));
            int countPosts = randomPageDoc.select("span.img_block2.img_block_big").size();

//Connect to preview random image page
            int randomKey = 1 + (int)(Math.random() * (countPosts));
            Document previewPic = Jsoup.connect(URL_PREVIEW_PIC + urlsPreviewPic.get(randomKey - 1)).get();
            String endUrl = previewPic.select("a.svelte-syqq5k").attr("href").substring(46);
            String src = urlsPreviewSrc.get(randomKey - 1).substring(34, urlsPreviewSrc.get(randomKey - 1).indexOf("_cp"));
            urlsPreviewPic.clear();
            urlsPreviewSrc.clear();
            return FINAL_START_URL + src + endUrl.substring(endUrl.indexOf(".")) + FINAL_MIDDLE_URL + endUrl;
        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private InlineKeyboardMarkup inlineKeyboardMarkup(String[] requestBtn){
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
            for (int i = 0; i < requestBtn.length; i++) {
                var button = new InlineKeyboardButton();
                if (requestBtn[i].startsWith("http")){
                    button.setText("Download");
                    button.setUrl(downloadImage(requestBtn[i]));
                } else if (requestBtn[i].equals("Subscribe") || requestBtn[i].equals("Unsubscribe")){
                    button.setText(requestBtn[i]);
                    button.setCallbackData(requestBtn[i] + requestBtn[0]);
                } else {
                    button.setText(requestBtn[i]);
                    button.setCallbackData(requestBtn[i]);
                }
                if (i > 2)
                    rowInline2.add(button);
                else
                    rowInline.add(button);
            }
        rowsInline.add(rowInline);
        rowsInline.add(rowInline2);
        markup.setKeyboard(rowsInline);
        return markup;
    }

    private File resizePic(String url){
        try {
            String destinationFile = "Anipic.jpg";
            BufferedImage pic = ImageIO.read(new URL(url));
            String format = (url.substring(url.lastIndexOf(".")+1));
            BufferedImage scaledImage = Scalr.resize(pic, 1280);
            File file = new File(destinationFile);
            ImageIO.write(scaledImage, format, file);
            return file;
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private String downloadImage(String url){
        return FINAL_DOWNLOAD_URL + url.substring(url.indexOf("ANIME-PICTURES.NET_-_")+21);
    }

    private void countOfSub(long chatId, long messageId){
        deleteMessage(chatId, messageId);
        int count = subs.get(chatId).listSizeSub();
        if (count == 0){
            sendMessage(chatId, "You don't have subscriptions");
            return;
        }
        subs.get(chatId).setPersonalSubs(new PriorityQueue<>(subs.get(chatId).getKey()));
        sendMessage(chatId, "Number of  subscriptions: " + count, inlineKeyboardMarkup(new String[]{"View"}));
    }

    @Component
    public class ScheduleSub {

        @Scheduled(fixedDelay = 3600000)
        public void schedule(){
            if (subs.isEmpty())
                return;

            for (var key : subs.keySet()){
                for (var req : subs.get(key).getKey()){
                    String url = downloadLastPic(subs.get(key).getValue(req));
                    if (subs.get(key).getValue(req).equals(url))
                        continue;
                    subs.get(key).setSub(req, url);
                    sendPhoto(key, new InputFile(resizePic(url)), inlineKeyboardMarkup(new String[]{req,
                                    subs.get(key).getValue(req), "Unsubscribe"}), "Subscription");
                }
            }
        }

        private String downloadLastPic(String request){
            StringBuilder UrlPreviewPic = new StringBuilder("https://anime-pictures.net/pictures/view_posts/0?search_tag=//&order_by=date&ldate=0&lang=en");
            try {
                Document doc = Jsoup.connect(String.valueOf(UrlPreviewPic.insert(60, request))).get();
                String urlPreviewPic = doc.selectFirst("span.img_block_big a").attr("href");
                String src = doc.selectFirst("span.img_block_big img").attr("src");
                Document previewPic = Jsoup.connect(URL_PREVIEW_PIC + urlPreviewPic).get();
                String endUrl = previewPic.select("div#big_preview_cont a").attr("href").substring(20);
                src = src.substring(34, src.indexOf("_cp"));
                return FINAL_START_URL + src + endUrl.substring(endUrl.indexOf(".")) + FINAL_MIDDLE_URL + endUrl;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
