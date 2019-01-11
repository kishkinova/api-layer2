/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.mfaas.error.impl;

import com.ca.mfaas.error.ErrorService;
import com.ca.mfaas.rest.response.ApiMessage;
import com.ca.mfaas.rest.response.Message;
import com.ca.mfaas.rest.response.MessageType;
import com.ca.mfaas.rest.response.impl.BasicApiMessage;
import com.ca.mfaas.rest.response.impl.BasicMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IllegalFormatConversionException;
import java.util.List;

/**
 * Default implementation of {@link ErrorService} that uses messages.yml as source for messages.
 */
public class ErrorServiceImpl implements ErrorService {
    private static final String COMMON_MESSAGES = "/mfs-common-messages.yml";
    private static final String INVALID_KEY_MESSAGE = "com.ca.mfaas.common.invalidMessageKey";
    private static final String INVALID_MESSAGE_TEXT_FORMAT = "com.ca.mfaas.common.invalidMessageTextFormat";
    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorServiceImpl.class);

    private final ErrorMessageStorage messageStorage;

    /**
     * Constructor that creates only common messages.
     */
    @SuppressWarnings("squid:S00112")
    public ErrorServiceImpl() {
        messageStorage = new ErrorMessageStorage();
        try (InputStream in = ErrorServiceImpl.class.getResourceAsStream(COMMON_MESSAGES)) {
            Yaml yaml = new Yaml();
            ErrorMessages messages = yaml.loadAs(in, ErrorMessages.class);
            messageStorage.addMessages(messages);
        } catch (YAMLException | IOException e) {
            throw new RuntimeException("There is problem with reading common messages file: " + COMMON_MESSAGES +
                ", " + e.getMessage(), e);
        }
    }

    /**
     * Constructor that creates common messages and messages from file.
     * @param messagesFilePath path to file with messages.
     */
    @SuppressWarnings("squid:S00112")
    public ErrorServiceImpl(String messagesFilePath) {
        this();
        try (InputStream in = ErrorServiceImpl.class.getResourceAsStream(messagesFilePath)) {
            Yaml yaml = new Yaml();
            ErrorMessages applicationMessages = yaml.loadAs(in, ErrorMessages.class);
            messageStorage.addMessages(applicationMessages);
        } catch (YAMLException | IOException e) {
            throw new RuntimeException("There is problem with reading application messages file: " + messagesFilePath, e);
        }
    }

    /**
     * Creates {@link ApiMessage} with key and list of parameters.
     * @param key of message in messages.yml file
     * @param parameters for message
     * @return {@link ApiMessage}
     */
    @Override
    public ApiMessage createApiMessage(String key, Object... parameters) {
        Message message = createMessage(key, parameters);
        return new BasicApiMessage(Collections.singletonList(message));
    }

    /**
     * Creates {@link ApiMessage} with list of {@link Message}.
     * @param key of message in messages.yml file
     * @param parameters list that contains arrays of parameters
     * @return {@link ApiMessage}
     */
    @Override
    public ApiMessage createApiMessage(String key, List<Object[]> parameters) {
        List<Message> messageList = new ArrayList<>();
        for (Object[] ob : parameters) {
            messageList.add(createMessage(key, ob));
        }
        return new BasicApiMessage(messageList);
    }

    /**
     * Internal method that call {@link ErrorMessageStorage} to get message by key.
     * @param key of message.
     * @param parameters array of parametes for message.
     * @return
     */
    private Message createMessage(String key, Object... parameters) {
        ErrorMessage message = messageStorage.getErrorMessage(key);
        message = validateMessage(message, key, parameters);

        String text;
        try {
            text = String.format(message.getText(), parameters);
        } catch (IllegalFormatConversionException exception) {
            LOGGER.debug("Internal error: Invalid message format was used", exception);
            message = messageStorage.getErrorMessage(INVALID_MESSAGE_TEXT_FORMAT);
            message = validateMessage(message, key, parameters);
            text = String.format(message.getText(), parameters);
        }

        return new BasicMessage(key, message.getType(), message.getNumber(), text);
    }

    private ErrorMessage validateMessage(ErrorMessage message, String key, Object... parameters) {
        if (message == null) {
            LOGGER.debug("Invalid message key '{}' was used. Please resolve this problem.", key);
            message = messageStorage.getErrorMessage(INVALID_KEY_MESSAGE);
            parameters[0] = key;
        }

        if (message == null) {
            String text = "Internal error: Invalid message key '%s' provided. No default message found. Please contact CA support of further assistance.";
            message = new ErrorMessage(INVALID_KEY_MESSAGE, "MFS0001", MessageType.ERROR, text);
            parameters[0] = key;
        }

        return message;
    }
}
