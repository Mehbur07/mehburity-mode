package com.mehbur.mehburity.ai;

/** One message of the conversation history: from the player or from Mehburity. */
public record ChatTurn(boolean fromPlayer, String text) {
}
