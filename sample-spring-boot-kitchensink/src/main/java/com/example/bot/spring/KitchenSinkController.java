/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.example.bot.spring;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.common.io.ByteStreams;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.event.BeaconEvent;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.FollowEvent;
import com.linecorp.bot.model.event.JoinEvent;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.UnfollowEvent;
import com.linecorp.bot.model.event.message.AudioMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.LocationMessageContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.message.VideoMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.RoomSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.message.AudioMessage;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.ImagemapMessage;
import com.linecorp.bot.model.message.LocationMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.StickerMessage;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.VideoMessage;
import com.linecorp.bot.model.message.imagemap.ImagemapArea;
import com.linecorp.bot.model.message.imagemap.ImagemapBaseSize;
import com.linecorp.bot.model.message.imagemap.MessageImagemapAction;
import com.linecorp.bot.model.message.imagemap.URIImagemapAction;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.CarouselColumn;
import com.linecorp.bot.model.message.template.CarouselTemplate;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;


import com.google.cloud.translate.Detection;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.Translate.TranslateOption;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;

import java.util.List;

import com.google.common.collect.ImmutableList;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionScopes;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Base64;
import org.apache.commons.io.IOUtils;
@Slf4j
@LineMessageHandler
public class KitchenSinkController {
    @Autowired
    private LineMessagingClient lineMessagingClient;

    @EventMapping
    public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
        TextMessageContent message = event.getMessage();
                handleTextContent(event.getReplyToken(), event, message);
    }

    //@EventMapping
    public void handleStickerMessageEvent(MessageEvent<StickerMessageContent> event) {
        handleSticker(event.getReplyToken(), event.getMessage());
    }

    
    
    
    //@EventMapping
    public void handleLocationMessageEvent(MessageEvent<LocationMessageContent> event) {
        LocationMessageContent locationMessage = event.getMessage();
        reply(event.getReplyToken(), new LocationMessage(
                locationMessage.getTitle(),
                locationMessage.getAddress(),
                locationMessage.getLatitude(),
                locationMessage.getLongitude()
        ));
    }

    
   public String googleServiceKey = "{\n" +
"  \"type\": \"service_account\",\n" +
"  \"project_id\": \"skilled-mile-162716\",\n" +
"  \"private_key_id\": \"5101273c0f15f0a17b666350effb51d5c423604e\",\n" +
"  \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDZLJ8YHCAqYX+/\\n5lG2XUWt0V6kIAiyeWbDQw6Z+TLJkVKY5eGanMLfaUk0pMfWTDHCJqs4fOiT2vAG\\nptMW0rPsvpa3M9kk5+Gm8wvI0JfPvLl/ZjKanJs6RYSZ3Ya1k/Bc3Rbo2EM2Vxrw\\nCkcE8ond8CNrYWgK2Zsm3+76JwPovL231OQc+r73Z2SbZvR7oH8pvmLLJylcKEpv\\n+Z3v3oLU7X+RJXU3JJ4jX54wCvDpZYMWtnT8hJooYowWfKsWxB0b/IjdDezCrsZY\\nOgXvPcQmGgAXncnPvZBfbFQdg5T0IlI1JLedWZV5AGg1glPZLaeo1pyFR5apKEaX\\n+CAjYCJbAgMBAAECggEAV7SYj6EUMGltsS8vwslKUZcjdH7nZER5BtR2+iHUq+i/\\nhbYY9VrnrFgV02fUuKvO0IzTSx3Ow5+Anf8Tcr0nIq4ZqeULhccLr2OqV7A+Dww1\\nkcjRGPW0DsVydr0rIPuc77PuA50LD8//tf9AjTPyD6pic4REA1W8Pefj2CyXfI62\\nNx1+SGd6t7YLzswp3Jxf5w42RFBzpxB0/hKNEoii0Qf1L2n4W3uEAwJGEqG0Pj5x\\nkDvgl1pJiwdubn3AV78/sM1Fmgvk9wQdJDsyIvQLxmGRGn6G6oXROj0KLEpvrFZE\\ng/KP66kgnUoBnqHux0bS082mxKNjNjbL5JNBEBNoUQKBgQDzi0ZhqMLR8lFpnsjU\\nfc3A+og+2P++SrZI0+Nr0hQzqn4RELdktXlLW+YRc1hO4S2ZACdh1U8ZT//v5KMi\\noL6BvgQmMpEvDvPOz8fgh3jPewjqkLshzA6IOKoJ+Bu6xGwtEUnnYuodlXiaceb6\\nGfm2je2pVFnbgfa3SbVapm2JcwKBgQDkSBZTbXqlIfDkLkQ5HOrysRxrF1Zlwz4k\\nZ+2AouK4E3miSz1iqOHo497KSxZxob38V9Vz+0ctAuVPIqIclt+XDXyEN23Td0Rr\\nkdgecJREc7BV+UFzpbzxdZilWBy/DDlVZMjzH8wPS/8OUkKStyZNfXKuAtt9hjcj\\nxhdOnF5peQKBgQDHggWDBROrlz0YMApHAFPoTZQFIBDJGz0ehe2cqvj/piAl7LK/\\nnmYh1MOw8fOakp6e4uBgJbTpgH6iT4NQX6wQbs/JVs1WZoJVniMYDQJrvVd9iFi0\\nBAy3jOvGxOg6ZKRVev82vPIakBK/OqXDpjnJUZUqjL4bsuigF5KoEwRSfwKBgQCv\\nfGsNPz/k6a6Q+rAfZ4eFgXljKdGU8P44ZlxBYvX+o5oBlO1fhowDyAhgYlCikb/G\\n2I6SVjxk8bDtoKYWbDT9nbR2v1WCFlFWkAsfe1O/O1/292HFUUdqJwhtMssGYpNA\\nffWsUGlB6R3tGHds6bZcI2+hLTklyaNhsMoB+FrroQKBgQDqtgLF1qwGnWRHa/G0\\nrSIKo2c0Z47dxlhcFT13odCR3we5S4AhmJ9ADSn+4h4yhuQYh+aPO2MQ3dnsaFSd\\nhSzotaAD6dftohUMZLEE3TAdvRg8Z2UYFJKFx7siur8VUxzlubncO6CS4wMRlK1R\\nlaPSGZC++65HMbvK4zayoil1UQ==\\n-----END PRIVATE KEY-----\\n\",\n" +
"  \"client_email\": \"vision-api@skilled-mile-162716.iam.gserviceaccount.com\",\n" +
"  \"client_id\": \"107621297260006636867\",\n" +
"  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n" +
"  \"token_uri\": \"https://accounts.google.com/o/oauth2/token\",\n" +
"  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n" +
"  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/vision-api%40skilled-mile-162716.iam.gserviceaccount.com\"\n" +
"}";
    
    public Vision getVisionService() throws IOException, GeneralSecurityException {
        InputStream credentialStream = new ByteArrayInputStream(googleServiceKey.getBytes());
        GoogleCredential credential = GoogleCredential.fromStream(credentialStream).createScoped(VisionScopes.all());
        return new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), credential)
                .setApplicationName("API key 1")
                .build();
    }
    
    @EventMapping
    public void handleImageMessageEvent(MessageEvent<ImageMessageContent> event) throws IOException {
         handleHeavyContent(
                event.getReplyToken(),
                event.getMessage().getId(),
                responseBody -> {

                
        ImmutableList.Builder<AnnotateImageRequest> requests = ImmutableList.builder();
        requests.add(
                new AnnotateImageRequest()
                .setFeatures(ImmutableList.of(new Feature().setMaxResults(10).setType("TEXT_DETECTION")))
                .setImage(new Image().setContent(getStringImage(responseBody.getStream())))
        );
         requests.add(
                new AnnotateImageRequest()
                .setFeatures(ImmutableList.of(new Feature().setMaxResults(5).setType("LABEL_DETECTION")))
                .setImage(new Image().setContent(getStringImage(responseBody.getStream())))
        );
        Vision.Images.Annotate annotate = getVisionService().images().annotate(new BatchAnnotateImagesRequest().setRequests(requests.build()));
        annotate.setDisableGZipContent(true);
        BatchAnnotateImagesResponse batchResponse = annotate.execute();
        AnnotateImageResponse text = batchResponse.getResponses().get(0);
        AnnotateImageResponse label = batchResponse.getResponses().get(1);
        String words="";
        if(text.getFullTextAnnotation()!=null){
            words = text.getFullTextAnnotation().getText();
            log.info(">> : "+words);
        }else{
            for(EntityAnnotation e :  label.getLabelAnnotations()){
                 words+="\r\n" + e.getDescription();
            }
           log.info(">> : "+words);
        }
              this.replyText(
                        ((MessageEvent) event).getReplyToken(),
                        words
                );
            });
    }

   private String getStringImage(InputStream fin){
    try {
        byte[] bytes = IOUtils.toByteArray(fin);
        fin.read(bytes, 0, bytes.length);
        fin.close();
        return Base64.getEncoder().encodeToString(bytes);
    } catch (Exception ex) {
    }
    return null;
}
    
    
   // @EventMapping
    public void handleAudioMessageEvent(MessageEvent<AudioMessageContent> event) throws IOException {
        handleHeavyContent(
                event.getReplyToken(),
                event.getMessage().getId(),
                responseBody -> {
                    DownloadedContent mp4 = saveContent("mp4", responseBody);
                    reply(event.getReplyToken(), new AudioMessage(mp4.getUri(), 100));
                });
    }

    //@EventMapping
    public void handleVideoMessageEvent(MessageEvent<VideoMessageContent> event) throws IOException {
        // You need to install ffmpeg and ImageMagick.
        handleHeavyContent(
                event.getReplyToken(),
                event.getMessage().getId(),
                responseBody -> {
                    DownloadedContent mp4 = saveContent("mp4", responseBody);
                    DownloadedContent previewImg = createTempFile("jpg");
                    system("convert",
                           mp4.path + "[0]",
                           previewImg.path.toString());
                    reply(((MessageEvent) event).getReplyToken(),
                          new VideoMessage(mp4.getUri(), previewImg.uri));
                });
    }

    //@EventMapping
    public void handleUnfollowEvent(UnfollowEvent event) {
        log.info("unfollowed this bot: {}", event);
    }

    //@EventMapping
    public void handleFollowEvent(FollowEvent event) {
        String replyToken = event.getReplyToken();
        this.replyText(replyToken, "Got followed event");
    }

    //@EventMapping
    public void handleJoinEvent(JoinEvent event) {
        String replyToken = event.getReplyToken();
        this.replyText(replyToken, "Joined " + event.getSource());
    }

    //@EventMapping
    public void handlePostbackEvent(PostbackEvent event) {
        String replyToken = event.getReplyToken();
        this.replyText(replyToken, "Got postback " + event.getPostbackContent().getData());
    }

    //@EventMapping
    public void handleBeaconEvent(BeaconEvent event) {
        String replyToken = event.getReplyToken();
        this.replyText(replyToken, "Got beacon message " + event.getBeacon().getHwid());
    }

   // @EventMapping
    public void handleOtherEvent(Event event) {
        log.info("Received message(Ignored): {}", event);
    }

    private void reply(@NonNull String replyToken, @NonNull Message message) {
        reply(replyToken, Collections.singletonList(message));
    }

    private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
        try {
            BotApiResponse apiResponse = lineMessagingClient
                    .replyMessage(new ReplyMessage(replyToken, messages))
                    .get();
            log.info("Sent messages: {}", apiResponse);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void replyText(@NonNull String replyToken, @NonNull String message) {
        if (replyToken.isEmpty()) {
            throw new IllegalArgumentException("replyToken must not be empty");
        }
        if (message.length() > 1000) {
            message = message.substring(0, 1000 - 2) + "……";
        }
        InputStream credentialStream = new ByteArrayInputStream(googleServiceKey.getBytes());
      Translate translate = TranslateOptions.newBuilder().setCredentials(ServiceAccountCredentials.fromStream(credentialStream)).build().getService();

    // The text to translate
    
    List<Detection> detections = translate.detect(ImmutableList.of(message));
String language = "";
    for (Detection detection : detections) {
       // System.out.println(detection.getLanguage());
        language = detection.getLanguage();
    }
        
        Translation translation = null;
        if("en".equalsIgnoreCase(language)){
         translation = translate.translate(
            message,
            TranslateOption.sourceLanguage(language),
            TranslateOption.targetLanguage("th"));
        }else{
            translation =   translate.translate(
            message,
            TranslateOption.sourceLanguage(language),
            TranslateOption.targetLanguage("en"));
        }
 
        this.reply(replyToken, new TextMessage(message + " : "+translation.getTranslatedText()));
    }

    private void handleHeavyContent(String replyToken, String messageId,
                                    Consumer<MessageContentResponse> messageConsumer) {
        final MessageContentResponse response;
        try {
            response = lineMessagingClient.getMessageContent(messageId)
                                          .get();
        } catch (InterruptedException | ExecutionException e) {
            reply(replyToken, new TextMessage("Cannot get image: " + e.getMessage()));
            throw new RuntimeException(e);
        }
        messageConsumer.accept(response);
    }

    private void handleSticker(String replyToken, StickerMessageContent content) {
        reply(replyToken, new StickerMessage(
                content.getPackageId(), content.getStickerId())
        );
    }

    private void handleTextContent(String replyToken, Event event, TextMessageContent content)
            throws Exception {
        String text = content.getText();
        
        
        
        log.info("Got text message from {}: {}", replyToken, text);
        switch (text) {
            case "@profile": {
                String userId = (event.getSource().getUserId() != null ? event.getSource().getUserId() : event.getSource().getSenderId());
                if (userId != null) {
                    lineMessagingClient
                            .getProfile(userId)
                            .whenComplete((profile, throwable) -> {
                                if (throwable != null) {
                                    this.replyText(replyToken, throwable.getMessage());
                                    return;
                                }

                                this.reply(
                                        replyToken,
                                        Arrays.asList(new TextMessage(
                                                              "Display name: " + profile.getDisplayName()),
                                                      new TextMessage("Status message: "
                                                                      + profile.getStatusMessage()))
                                );

                            });
                } else {
                    this.replyText(replyToken, "Bot can't use profile API without user ID");
                }
                break;
            }
            case "@bye": {
                Source source = event.getSource();
                if (source instanceof GroupSource) {
                    this.replyText(replyToken, "Leaving group");
                    lineMessagingClient.leaveGroup(((GroupSource) source).getGroupId()).get();
                } else if (source instanceof RoomSource) {
                    this.replyText(replyToken, "Leaving room");
                    lineMessagingClient.leaveRoom(((RoomSource) source).getRoomId()).get();
                } else {
                    this.replyText(replyToken, "Bot can't leave from 1:1 chat");
                }
                break;
            }
            case "@confirm": {
                ConfirmTemplate confirmTemplate = new ConfirmTemplate(
                        "Do it?",
                        new MessageAction("Yes", "Yes!"),
                        new MessageAction("No", "No!")
                );
                TemplateMessage templateMessage = new TemplateMessage("Confirm alt text", confirmTemplate);
                this.reply(replyToken, templateMessage);
                break;
            }
            case "@buttons": {
                String imageUrl = createUri("/static/buttons/1040.jpg");
                ButtonsTemplate buttonsTemplate = new ButtonsTemplate(
                        imageUrl,
                        "My button sample",
                        "Hello, my button",
                        Arrays.asList(
                                new URIAction("Go to line.me",
                                              "https://line.me"),
                                new PostbackAction("Say hello1",
                                                   "hello こんにちは"),
                                new PostbackAction("言 hello2",
                                                   "hello こんにちは",
                                                   "hello こんにちは"),
                                new MessageAction("Say message",
                                                  "Rice=米")
                        ));
                TemplateMessage templateMessage = new TemplateMessage("Button alt text", buttonsTemplate);
                this.reply(replyToken, templateMessage);
                break;
            }
            case "@carousel": {
                String imageUrl = createUri("/static/buttons/1040.jpg");
                CarouselTemplate carouselTemplate = new CarouselTemplate(
                        Arrays.asList(
                                new CarouselColumn(imageUrl, "hoge", "fuga", Arrays.asList(
                                        new URIAction("Go to line.me",
                                                      "https://line.me"),
                                        new PostbackAction("Say hello1",
                                                           "hello こんにちは")
                                )),
                                new CarouselColumn(imageUrl, "hoge", "fuga", Arrays.asList(
                                        new PostbackAction("言 hello2",
                                                           "hello こんにちは",
                                                           "hello こんにちは"),
                                        new MessageAction("Say message",
                                                          "Rice=米")
                                ))
                        ));
                TemplateMessage templateMessage = new TemplateMessage("Carousel alt text", carouselTemplate);
                this.reply(replyToken, templateMessage);
                break;
            }
            case "@imagemap":
                this.reply(replyToken, new ImagemapMessage(
                        createUri("/static/rich"),
                        "This is alt text",
                        new ImagemapBaseSize(1040, 1040),
                        Arrays.asList(
                                new URIImagemapAction(
                                        "https://www.google.co.th/",
                                        new ImagemapArea(
                                                0, 0, 520, 520
                                        )
                                ),
                                new URIImagemapAction(
                                        "https://store.line.me/family/music/en",
                                        new ImagemapArea(
                                                520, 0, 520, 520
                                        )
                                ),
                                new URIImagemapAction(
                                        "https://store.line.me/family/play/en",
                                        new ImagemapArea(
                                                0, 520, 520, 520
                                        )
                                ),
                                new MessageImagemapAction(
                                        "URANAI!",
                                        new ImagemapArea(
                                                520, 520, 520, 520
                                        )
                                )
                        )
                ));
                break;
            default:
                log.info("Returns echo message {}: {}", replyToken, text);
                        if(text.indexOf(".")==0){
                this.replyText(
                        replyToken,
                        text.replace(".", "")
                );
                        }
                break;
        }
    }

    private static String createUri(String path) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                                          .path(path).build()
                                          .toUriString();
    }

    private void system(String... args) {
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        try {
            Process start = processBuilder.start();
            int i = start.waitFor();
            log.info("result: {} =>  {}", Arrays.toString(args), i);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            log.info("Interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private static DownloadedContent saveContent(String ext, MessageContentResponse responseBody) {
        log.info("Got content-type: {}", responseBody);

        DownloadedContent tempFile = createTempFile(ext);
        try (OutputStream outputStream = Files.newOutputStream(tempFile.path)) {
            ByteStreams.copy(responseBody.getStream(), outputStream);
            log.info("Saved {}: {}", ext, tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static DownloadedContent createTempFile(String ext) {
        String fileName = LocalDateTime.now().toString() + '-' + UUID.randomUUID().toString() + '.' + ext;
        Path tempFile = KitchenSinkApplication.downloadedContentDir.resolve(fileName);
        tempFile.toFile().deleteOnExit();
        return new DownloadedContent(
                tempFile,
                createUri("/downloaded/" + tempFile.getFileName()));
    }

    @Value
    public static class DownloadedContent {
        Path path;
        String uri;
    }
}
