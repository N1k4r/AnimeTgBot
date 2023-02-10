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
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
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
    static final String MIDDLE_URL = "?if=ANIME-PICTURES.NET_-_";
    static final String START_URL = "https://ip1.anime-pictures.net/direct-images/";
    static final String DOWNLOAD_URL = "https://anime-pictures.net/pictures/download_image/";
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
        ThreadTask task = new ThreadTask(update);
        task.start();
    }
    class ThreadTask extends Thread{
        Update update;
        public ThreadTask(Update update) {
            this.update = update;
        }
        @Override
        public void run() {
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
                String[] request = update.getCallbackQuery().getData().split(" ");
                switch (request[0]){
                    case "Subscribe" -> {
                        if (!(subs.containsKey(chatId)))
                            subs.put(chatId, new UserSubs());
                        if (subs.get(chatId).setSub(request[1]))
                            editMedia(chatId, messageId, "SubPic.jpg", inlineKeyboardMarkup(request[1], "Unsubscribe"));
                        else
                            sendMessage(chatId, "You have already subscribed");
                    }
                    case "Unsubscribe" -> {
                        if (subs.get(chatId).deleteSub(request[1]) && update.getCallbackQuery().getMessage().hasPhoto())
                            editMedia(chatId, messageId, "UnsubPic.jpg", inlineKeyboardMarkup(request[1], "Subscribe"));
                        else
                            sendMessage(chatId, "You have already cancelled your subscription");
                    }
                    case "View", "Next" -> {
                        String requestSub = subs.get(chatId).pollQueue();
                        String[] buttons = new String[]{requestSub, "Next", "Unsubscribe", "Exit"};
                        if (subs.get(chatId).emptyQueue())
                            buttons = new String[]{requestSub, "Unsubscribe", "Exit"};
                        editMessageText(chatId, messageId, lastMessage, inlineKeyboardMarkup(buttons));
                    }
                    case "Exit" -> deleteMessage(chatId, messageId);
                    default -> {
                        String url = downloadContent(request[0]);
                        String[] buttons = new String[]{request[0], url, "Subscribe"};
                        if (subs.containsKey(chatId) && subs.get(chatId).keyExist(request[0]))
                            buttons = new String[]{request[0], url, "Unsubscribe"};

                        if (lastMessage == null) {
                            editMedia(chatId, messageId, url, inlineKeyboardMarkup(buttons));
                            return;
                        }
                        editMessageText(chatId, messageId, "Loading...");
                        sendPhoto(chatId, new InputFile(resizePic(url)), inlineKeyboardMarkup(buttons));
                        deleteMessage(chatId, messageId);
                    }
                }
            }
            else
                sendMessage(update.getMessage().getChatId(), "I don't know what to do with it");
        }
    }

    private void sendMessage(long chatId, String textToSend, InlineKeyboardMarkup... markup) {
        SendMessage message = new SendMessage(String.valueOf(chatId), textToSend);
        if (markup.length > 0){
            message.setReplyMarkup(markup[0]);
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    private void deleteMessage(long chatId, long messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setMessageId((int) messageId);
        deleteMessage.setChatId(chatId);
        try {
            execute(deleteMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private Document getSearchPage(String request, int page){
        StringBuilder builder = new StringBuilder("https://anime-pictures.net/pictures/view_posts/0?search_tag=//&order_by=date&ldate=0&lang=en");
        String url = builder.insert(60, request).toString().replaceAll("posts/0", "posts/" + page);
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
            doc.select("a.desktop_only").remove();
            doc.select("a.mobile_only").remove();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return doc;
    }

    private String getUrlFullPic(String url){
        String urlPic = null;
        try {
            Document doc = Jsoup.connect(url).get();
            String src = doc.select("picture.svelte-syqq5k img").attr("src");
            src = src.substring(src.indexOf("previews/") + 9, src.indexOf("_"));
            String endUrl = doc.select("a.svelte-syqq5k").attr("href");
            endUrl = endUrl.substring(endUrl.indexOf("image/") + 6);
            String format = endUrl.substring(endUrl.indexOf("."));
            urlPic = START_URL + src + format + MIDDLE_URL + endUrl;
        } catch (IOException e){
            e.printStackTrace();
        }
        return urlPic;
    }

    private void searchResult(long chatId, String request){
        Document doc = getSearchPage(request, 0);
        doc.select("p.numeric_pages.svelte-ho8yi0").remove();
        String numberPic = doc.select("div.pagination.svelte-18faof").first().text().replaceAll("\\D", "");
        if (numberPic.equals("0")){
            sendMessage(chatId, "Pictures not found");
            return;
        }
        sendMessage(chatId, "Search pictures: " + numberPic, inlineKeyboardMarkup(request));
    }
    private String downloadContent(String request){
        ArrayList<String> url = new ArrayList<>();

//Connect to request page
        Document doc = getSearchPage(request, 0);
        int pages = 0;
        if ((doc.select("p.numeric_pages.svelte-ho8yi0").hasAttr("href")))
            pages = Integer.parseInt(doc.select("p.numeric_pages.svelte-ho8yi0 a").last().text());

//Connect to random page
        int randomPage = (int)(Math.random() * (pages + 1));
        Document randomPageDoc = getSearchPage(request, randomPage);
        Elements posts = randomPageDoc.select("span.img_block2.img_block_big");
        posts.forEach(postUrl -> url.add(postUrl.select("a").attr("href")));
        int randomKey = 1 + (int)(Math.random() * (url.size()));
        return  getUrlFullPic(URL_PREVIEW_PIC + url.get(randomKey - 1));
    }

    private InlineKeyboardMarkup inlineKeyboardMarkup(String... requestBtn){
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
                    button.setCallbackData(requestBtn[i] + " " + requestBtn[0]);
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
        String destinationFile = "content/" + url.replaceAll("\\D", "") + ".jpg";
        File file = new File(destinationFile);
        try{
            BufferedImage pic = ImageIO.read(new URL(url));
            String format = (url.substring(url.lastIndexOf(".")+1));
            BufferedImage scaledImage = Scalr.resize(pic, 1280);
            ImageIO.write(scaledImage, format, file);
        }catch (Exception e){
            e.printStackTrace();
        }
        return file;
    }

    private String downloadImage(String url){
        return DOWNLOAD_URL + url.substring(url.indexOf("ANIME-PICTURES.NET_-_")+21);
    }

    private void countOfSub(long chatId, long messageId){
        deleteMessage(chatId, messageId);
        int count = subs.get(chatId).listSizeSub();
        if (count == 0){
            sendMessage(chatId, "You don't have subscriptions");
            return;
        }
        subs.get(chatId).setPersonalSubs(new PriorityQueue<>(subs.get(chatId).getKey()));
        sendMessage(chatId, "Number of  subscriptions: " + count, inlineKeyboardMarkup("View"));
    }

    @Component
    public class ScheduleSub {
        @Scheduled(fixedDelay = 10000)
        public void schedule(){
            File[] file = new File("content/").listFiles();
            for (int i = 0; i < file.length - 1; i++) {
                if (file[i].canRead())
                    file[i].delete();
            }
            if (subs.isEmpty())
                return;
            for (var key : subs.keySet()){
                for (var req : subs.get(key).getKey()){
                    String url = downloadLastPic(req);
                    if (subs.get(key).getValue(req).equals(url))
                        continue;
                    subs.get(key).setLastUrlPic(req, url);
                    sendPhoto(key, new InputFile(resizePic(url)), inlineKeyboardMarkup(url, "Unsubscribe"), "Subscribe");
                }
            }
        }

        private String downloadLastPic(String request){
            Document doc = getSearchPage(request, 0);
            String urlPreviewPic = doc.selectFirst("span.img_block2.img_block_big a").attr("href");
            return getUrlFullPic(URL_PREVIEW_PIC + urlPreviewPic);
        }
    }
}
