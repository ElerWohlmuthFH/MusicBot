package com.jagrosh.jmusicbot;

import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SlaveListener extends ListenerAdapter {
    private final Bot bot;

    public SlaveListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Slave bot listens for a message from the master bot (could be forwarded command)
        if (event.getAuthor().isBot() && isMasterBot(event.getAuthor().getId())) {
            String command = event.getMessage().getContentRaw();
            if (command.startsWith("!play")) {
                // Handle the play command
                handlePlayCommand(command, event);
            }
        }
    }

    private boolean isMasterBot(String botId) {
        // Logic to check if the message is from the master bot
        return botId.equals("TOKEN1");  // Replace with actual master bot ID
    }

    private void handlePlayCommand(String command, MessageReceivedEvent event) {
        // Extract the song name or URL from the command
        String song = command.replaceFirst("!play", "").trim();

        // Logic to make the slave bot join the voice channel and play the music
        VoiceChannel userVoiceChannel = event.getMember().getVoiceState().getChannel();
        if (userVoiceChannel == null) {
            event.getChannel().sendMessage("You must be in a voice channel to use the play command.").queue();
            return;
        }

        if (!event.getGuild().getSelfMember().getVoiceState().inVoiceChannel()) {
            event.getGuild().getAudioManager().openAudioConnection(userVoiceChannel);
            event.getChannel().sendMessage("Slave bot is joining your voice channel and playing the song...").queue();
        }

        // Load and play the song using the bot's audio player
        // You can use Lavaplayer or any other music playing library to load and play the track
        playMusic(song, event);
    }

    private void playMusic(String song, MessageReceivedEvent event) {
        // Implement the logic to load and play the music (using Lavaplayer or similar)
        event.getChannel().sendMessage("Now playing: " + song).queue();
    }
}
