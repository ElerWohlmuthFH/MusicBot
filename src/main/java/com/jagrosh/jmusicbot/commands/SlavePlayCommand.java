package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.managers.AudioManager;

public class SlavePlayCommand extends MusicCommand {
    private final Bot bot;  // Bot instance
    private final Guild guild;  // Guild associated with the bot
    private final TextChannel textChannel;  // Text channel where the command is issued

    public SlavePlayCommand(Bot bot, Guild guild, TextChannel textChannel) {
        super(bot);  // Pass the Bot instance to the parent MusicCommand class
        this.bot = bot;  // Assign the Bot instance
        this.guild = guild;  // Assign the Guild
        this.textChannel = textChannel;  // Assign the TextChannel
    }

    @Override
    public void doCommand(CommandEvent event) {
        // Not used, but required to override
    }

    // Executes the play command
    public void execute(MessageReceivedEvent event) {
        String args = event.getMessage().getContentRaw().replaceFirst("!play", "").trim();

        if (args.isEmpty()) {
            textChannel.sendMessage("Please provide a song title or URL!").queue();
            return;
        }

        // Call joinAndPlay with the correct Bot instance
        joinAndPlay(bot, event, args);
    }

    // Method to handle joining the voice channel and playing music
    private void joinAndPlay(Bot bot, MessageReceivedEvent event, String track) {
        Guild guild = event.getGuild();
        AudioManager audioManager = guild.getAudioManager();

        // Ensure AudioHandler is set if it's not already
        AudioHandler handler = bot.getAudioHandlerForGuild(guild);  // Use method to get AudioHandler
        if (audioManager.getSendingHandler() == null) {
            audioManager.setSendingHandler(handler);  // Set the handler here
        }

        // Join the same voice channel as the user who issued the command
        Member member = event.getMember();
        if (member != null && member.getVoiceState() != null && member.getVoiceState().inVoiceChannel()) {
            loadAndPlay(track, event);
        } else {
            event.getChannel().sendMessage("You need to be in a voice channel to play music!").queue();
        }
    }

    // Method to load and play the provided track
    private void loadAndPlay(String trackUrl, MessageReceivedEvent event) {
        textChannel.sendMessage("Loading... [" + trackUrl + "]").queue(m -> {
            bot.getPlayerManager().loadItemOrdered(guild, trackUrl, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    if (handler == null) {
                        textChannel.sendMessage("Error: AudioHandler is not initialized.").queue();
                        System.out.println("Error: AudioHandler is null.");
                        return;
                    }

                    RequestMetadata metadata = RequestMetadata.fromMessageReceivedEvent(track, event);
                    int pos = handler.addTrack(new QueuedTrack(track, metadata));
                    textChannel.sendMessage("Added **" + track.getInfo().title + "** (`" + TimeUtil.formatTime(track.getDuration()) + "`) to the queue at position " + pos + ".").queue();
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    if (handler == null) {
                        textChannel.sendMessage("Error: AudioHandler is not initialized.").queue();
                        System.out.println("Error: AudioHandler is null.");
                        return;
                    }

                    AudioTrack firstTrack = playlist.getSelectedTrack() != null ? playlist.getSelectedTrack() : playlist.getTracks().get(0);
                    RequestMetadata metadata = RequestMetadata.fromMessageReceivedEvent(firstTrack, event);
                    handler.addTrack(new QueuedTrack(firstTrack, metadata));
                    textChannel.sendMessage("Playing playlist **" + playlist.getName() + "** with " + playlist.getTracks().size() + " tracks.").queue();
                }

                @Override
                public void noMatches() {
                    textChannel.sendMessage("No matches found for `" + trackUrl + "`.").queue();
                    System.out.println("No matches found for track: " + trackUrl);
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    textChannel.sendMessage("Could not play: " + exception.getMessage()).queue();
                    exception.printStackTrace();  // Log stack trace for debugging
                }
            });
        });
    }
}
