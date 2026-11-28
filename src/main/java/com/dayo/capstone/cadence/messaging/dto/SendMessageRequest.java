package com.dayo.capstone.cadence.messaging.dto;

import com.dayo.capstone.cadence.messaging.model.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {

    @NotBlank(message = "Write a message first")
    @Size(max = Message.MAX_BODY, message = "A message can be at most 4000 characters")
    private String body;
}
