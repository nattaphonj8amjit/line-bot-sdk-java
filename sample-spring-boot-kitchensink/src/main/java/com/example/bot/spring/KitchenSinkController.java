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

	@EventMapping
	public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) throws Exception {
		TextMessageContent message = event.getMessage();
		handleTextContent(event.getReplyToken(), event, message);
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
						translation = translate.translate(words, TranslateOption.sourceLanguage("ja"),
								TranslateOption.targetLanguage("en"), TranslateOption.model("nmt"));
						translation = translate.translate(translation.getTranslatedText(),
								TranslateOption.sourceLanguage("en"), TranslateOption.targetLanguage("th"),
								TranslateOption.model("nmt"));
						this.reply(((MessageEvent) event).getReplyToken(), new TextMessage(translation.getTranslatedText()));
					} else if ("en".equalsIgnoreCase(language)) {
						translation = translate.translate(translation.getTranslatedText(),
								TranslateOption.sourceLanguage("en"), TranslateOption.targetLanguage("th"),
								TranslateOption.model("nmt"));
						this.reply(((MessageEvent) event).getReplyToken(), new TextMessage(translation.getTranslatedText()));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		});

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

	private void replyText(@NonNull String replyToken, @NonNull String message) {
		if (replyToken.isEmpty()) {
			throw new IllegalArgumentException("replyToken must not be empty");
		}
		if (message.length() > 1000) {
			message = message.substring(0, 1000 - 2) + "……";
		}
		// The text to translate
		Translate translate = getTranslateService();
		List<Detection> detections = translate.detect(ImmutableList.of(message));
		String language = "";
		for (Detection detection : detections) {
			language = detection.getLanguage();
		}
		Translation translation = null;
		if ("en".equalsIgnoreCase(language)) {
			translation = translate.translate(message, TranslateOption.sourceLanguage(language),
					TranslateOption.targetLanguage("th"), TranslateOption.model("nmt"));
		}
		this.reply(replyToken, new TextMessage(translation.getTranslatedText()));

	}

	private void handleTextContent(String replyToken, Event event, TextMessageContent content) throws Exception {
		String text = content.getText();
		log.info("Got text message from {}: {}", replyToken, text);
		if (text.indexOf(".") == 0) {
			this.replyText(replyToken, text.replace(".", ""));
		}
	}

	private Translate getTranslateService() throws Exception {
		return TranslateOptions.newBuilder()
				.setCredentials(ServiceAccountCredentials
						.fromStream(new ClassPathResource("static/ggc_api_key.json").getInputStream()))
				.build().getService();
	}

	private Vision getVisionService() throws Exception {
		GoogleCredential credential = GoogleCredential
				.fromStream(new ClassPathResource("static/ggc_api_key.json").getInputStream())
				.createScoped(VisionScopes.all());
		return new Vision.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(),
				credential).build();
	}

}
