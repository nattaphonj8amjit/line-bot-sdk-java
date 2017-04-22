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
import org.springframework.core.io.ClassPathResource;

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
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.translate.Detection;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.Translate.TranslateOption;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import com.google.common.collect.ImmutableList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;

import org.apache.commons.io.IOUtils;

@Slf4j
@LineMessageHandler
public class KitchenSinkController {
	@Autowired
	private LineMessagingClient lineMessagingClient;

	// @EventMapping
	public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
		TextMessageContent message = event.getMessage();
		handleTextContent(event.getReplyToken(), event, message);
	}

	// @EventMapping
	public void handleStickerMessageEvent(MessageEvent<StickerMessageContent> event) {
		handleSticker(event.getReplyToken(), event.getMessage());
	}

	// @EventMapping
	public void handleLocationMessageEvent(MessageEvent<LocationMessageContent> event) {
		LocationMessageContent locationMessage = event.getMessage();
		reply(event.getReplyToken(), new LocationMessage(locationMessage.getTitle(), locationMessage.getAddress(),
				locationMessage.getLatitude(), locationMessage.getLongitude()));
	}

	public Vision getVisionService() throws Exception {
		GoogleCredential credential = GoogleCredential
				.fromStream(new ClassPathResource("static/datastoreowner.json").getInputStream())
				.createScoped(VisionScopes.all());
		return new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(),
				credential).build();
	}

	@EventMapping
	public void handleImageMessageEvent(MessageEvent<ImageMessageContent> event) throws Exception {
		handleHeavyContent(event.getReplyToken(), event.getMessage().getId(), responseBody -> {

			try {
				ImmutableList.Builder<AnnotateImageRequest> requests = ImmutableList.builder();
				String base64img = getStringImage(responseBody.getStream());
				requests.add(new AnnotateImageRequest()
						.setFeatures(ImmutableList.of(new Feature().setMaxResults(1).setType("TEXT_DETECTION")))
						.setImage(new Image().setContent(base64img)));

				Vision.Images.Annotate annotate = getVisionService().images()
						.annotate(new BatchAnnotateImagesRequest().setRequests(requests.build()));
				annotate.setDisableGZipContent(true);
				BatchAnnotateImagesResponse batchResponse = annotate.execute();
				AnnotateImageResponse textResponse = batchResponse.getResponses().get(0);
				// AnnotateImageResponse labelResponse =
				// batchResponse.getResponses().get(1);
				String words = "";
				if (textResponse.getFullTextAnnotation() != null) {
					words = textResponse.getFullTextAnnotation().getText();
					log.info(">> : " + words);
					Translate translate = getTranslateService();
					List<Detection> detections = translate.detect(ImmutableList.of(words));
					String language = "";
					Translation translation = null;
					for (Detection detection : detections) {
						language = detection.getLanguage();
					}
					if ("ja".equalsIgnoreCase(language)) {
//						translation = translate.translate(words, TranslateOption.sourceLanguage("ja"),
//								TranslateOption.targetLanguage("th"), TranslateOption.model("nmt"));
//						String jpToTh = translation.getTranslatedText();
						translation = translate.translate(words, TranslateOption.sourceLanguage("ja"),
								TranslateOption.targetLanguage("en"), TranslateOption.model("nmt"));
						String jpToEn = translation.getTranslatedText();
						translation = translate.translate(translation.getTranslatedText(),
								TranslateOption.sourceLanguage("en"), TranslateOption.targetLanguage("th"),
								TranslateOption.model("nmt"));
						String jpToEnToTh = translation.getTranslatedText();
//	this.reply(((MessageEvent) event).getReplyToken(),
//								new TextMessage("JP > EN : " + jpToEn + "\r\n"+ "JP > TH : "+jpToTh+"\r\n"+"JP > EN > TH : "+jpToEnToTh ));
	this.reply(((MessageEvent) event).getReplyToken(),
			new TextMessage(jpToEnToTh ));

					}

				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		});

	}

	private String getStringImage(InputStream fin) {
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
		handleHeavyContent(event.getReplyToken(), event.getMessage().getId(), responseBody -> {
			DownloadedContent mp4 = saveContent("mp4", responseBody);
			reply(event.getReplyToken(), new AudioMessage(mp4.getUri(), 100));
		});
	}

	// @EventMapping
	public void handleVideoMessageEvent(MessageEvent<VideoMessageContent> event) throws IOException {
		// You need to install ffmpeg and ImageMagick.
		handleHeavyContent(event.getReplyToken(), event.getMessage().getId(), responseBody -> {
			DownloadedContent mp4 = saveContent("mp4", responseBody);
			DownloadedContent previewImg = createTempFile("jpg");
			system("convert", mp4.path + "[0]", previewImg.path.toString());
			reply(((MessageEvent) event).getReplyToken(), new VideoMessage(mp4.getUri(), previewImg.uri));
		});
	}

	// @EventMapping
	public void handleUnfollowEvent(UnfollowEvent event) {
		log.info("unfollowed this bot: {}", event);
	}

	// @EventMapping
	public void handleFollowEvent(FollowEvent event) {
		String replyToken = event.getReplyToken();
		this.replyText(replyToken, "Got followed event");
	}

	// @EventMapping
	public void handleJoinEvent(JoinEvent event) {
		String replyToken = event.getReplyToken();
		this.replyText(replyToken, "Joined " + event.getSource());
	}

	// @EventMapping
	public void handlePostbackEvent(PostbackEvent event) {
		String replyToken = event.getReplyToken();
		this.replyText(replyToken, "Got postback " + event.getPostbackContent().getData());
	}

	// @EventMapping
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
			BotApiResponse apiResponse = lineMessagingClient.replyMessage(new ReplyMessage(replyToken, messages)).get();
			log.info("Sent messages: {}", apiResponse);
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	private Translate getTranslateService() throws Exception {
		return TranslateOptions.newBuilder()
				.setCredentials(ServiceAccountCredentials
						.fromStream(new ClassPathResource("static/datastoreowner.json").getInputStream()))
				.build().getService();
	}

	private void replyText(@NonNull String replyToken, @NonNull String message) {
		if (replyToken.isEmpty()) {
			throw new IllegalArgumentException("replyToken must not be empty");
		}
		if (message.length() > 1000) {
			message = message.substring(0, 1000 - 2) + "……";
		}

		try {

			// The text to translate
			Translate translate = getTranslateService();
			List<Detection> detections = translate.detect(ImmutableList.of(message));
			String language = "";
			for (Detection detection : detections) {
				language = detection.getLanguage();
			}

			Translation translation = null;

			if ("ja".equalsIgnoreCase(language)) {
				translation = translate.translate(message, TranslateOption.sourceLanguage(language),
						TranslateOption.targetLanguage("th"), TranslateOption.model("nmt"));
			} else {
				translation = translate.translate(message, TranslateOption.sourceLanguage(language),
						TranslateOption.targetLanguage("ja"), TranslateOption.model("nmt"));
			}

			this.reply(replyToken, new TextMessage(message + " : " + translation.getTranslatedText()));
		} catch (Exception e) {
		}
	}

	private void handleHeavyContent(String replyToken, String messageId,
			Consumer<MessageContentResponse> messageConsumer) {
		final MessageContentResponse response;
		try {
			response = lineMessagingClient.getMessageContent(messageId).get();
		} catch (InterruptedException | ExecutionException e) {
			reply(replyToken, new TextMessage("Cannot get image: " + e.getMessage()));
			throw new RuntimeException(e);
		}
		messageConsumer.accept(response);
	}

	private void handleSticker(String replyToken, StickerMessageContent content) {
		reply(replyToken, new StickerMessage(content.getPackageId(), content.getStickerId()));
	}

	private void handleTextContent(String replyToken, Event event, TextMessageContent content) throws Exception {
		String text = content.getText();

		log.info("Got text message from {}: {}", replyToken, text);
		switch (text) {
		case "@profile": {
			String userId = (event.getSource().getUserId() != null ? event.getSource().getUserId()
					: event.getSource().getSenderId());
			if (userId != null) {
				lineMessagingClient.getProfile(userId).whenComplete((profile, throwable) -> {
					if (throwable != null) {
						this.replyText(replyToken, throwable.getMessage());
						return;
					}

					this.reply(replyToken, Arrays.asList(new TextMessage("Display name: " + profile.getDisplayName()),
							new TextMessage("Status message: " + profile.getStatusMessage())));

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
			ConfirmTemplate confirmTemplate = new ConfirmTemplate("Do it?", new MessageAction("Yes", "Yes!"),
					new MessageAction("No", "No!"));
			TemplateMessage templateMessage = new TemplateMessage("Confirm alt text", confirmTemplate);
			this.reply(replyToken, templateMessage);
			break;
		}
		case "@buttons": {
			String imageUrl = createUri("/static/buttons/1040.jpg");
			ButtonsTemplate buttonsTemplate = new ButtonsTemplate(imageUrl, "My button sample", "Hello, my button",
					Arrays.asList(new URIAction("Go to line.me", "https://line.me"),
							new PostbackAction("Say hello1", "hello こんにちは"),
							new PostbackAction("言 hello2", "hello こんにちは", "hello こんにちは"),
							new MessageAction("Say message", "Rice=米")));
			TemplateMessage templateMessage = new TemplateMessage("Button alt text", buttonsTemplate);
			this.reply(replyToken, templateMessage);
			break;
		}
		case "@carousel": {
			String imageUrl = createUri("/static/buttons/1040.jpg");
			CarouselTemplate carouselTemplate = new CarouselTemplate(Arrays.asList(
					new CarouselColumn(imageUrl, "hoge", "fuga",
							Arrays.asList(new URIAction("Go to line.me", "https://line.me"),
									new PostbackAction("Say hello1", "hello こんにちは"))),
					new CarouselColumn(imageUrl, "hoge", "fuga",
							Arrays.asList(new PostbackAction("言 hello2", "hello こんにちは", "hello こんにちは"),
									new MessageAction("Say message", "Rice=米")))));
			TemplateMessage templateMessage = new TemplateMessage("Carousel alt text", carouselTemplate);
			this.reply(replyToken, templateMessage);
			break;
		}
		case "@imagemap":
			this.reply(replyToken, new ImagemapMessage(createUri("/static/rich"), "This is alt text",
					new ImagemapBaseSize(1040, 1040),
					Arrays.asList(new URIImagemapAction("https://www.google.co.th/", new ImagemapArea(0, 0, 520, 520)),
							new URIImagemapAction("https://store.line.me/family/music/en",
									new ImagemapArea(520, 0, 520, 520)),
							new URIImagemapAction("https://store.line.me/family/play/en",
									new ImagemapArea(0, 520, 520, 520)),
							new MessageImagemapAction("URANAI!", new ImagemapArea(520, 520, 520, 520)))));
			break;
		default:
			log.info("Returns echo message {}: {}", replyToken, text);
			if (text.indexOf(".") == 0) {
				this.replyText(replyToken, text.replace(".", ""));
			}
			break;
		}
	}

	private static String createUri(String path) {
		return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).build().toUriString();
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
		return new DownloadedContent(tempFile, createUri("/downloaded/" + tempFile.getFileName()));
	}

	@Value
	public static class DownloadedContent {
		Path path;
		String uri;
	}
}
