package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.TimeUtil;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;


public class SlavePlayCommand extends MusicCommand {
    private final JDA slaveBot;
    private final Guild guild;
    private final TextChannel textChannel;

    public SlavePlayCommand(Bot bot, JDA slaveBot, Guild guild, TextChannel textChannel) {
        super(bot);  // Pass the existing Bot instance
        this.slaveBot = slaveBot;
        this.guild = guild;
        this.textChannel = textChannel;
    }

    @Override
    public void doCommand(CommandEvent event) {
        // Not using CommandEvent, leave this empty
    }

    // Method to handle the play command with a MessageReceivedEvent
    public void execute(MessageReceivedEvent event) {
        String args = event.getMessage().getContentRaw().replaceFirst("!play", "").trim();

        if (args.isEmpty()) {
            textChannel.sendMessage("You must provide a song title or URL!").queue();
            return;
        }

        VoiceChannel userVoiceChannel = event.getMember().getVoiceState().getChannel();
        if (userVoiceChannel == null) {
            textChannel.sendMessage("You must be in a voice channel to use the play command!").queue();
            return;
        }

        if (!guild.getSelfMember().getVoiceState().inVoiceChannel()) {
            guild.getAudioManager().openAudioConnection(userVoiceChannel);
            textChannel.sendMessage("Joining your voice channel...").queue();
        }

        // Pass the 'event' object along with the track URL to loadAndPlay
        loadAndPlay(args, event);
    }


    private void loadAndPlay(String trackUrl, MessageReceivedEvent event) {
        textChannel.sendMessage("Loading... [" + trackUrl + "]").queue(m -> {
            bot.getPlayerManager().loadItemOrdered(guild, trackUrl, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    // Use the overloaded fromResultHandler method with MessageReceivedEvent
                    int pos = handler.addTrack(new QueuedTrack(track, RequestMetadata.fromResultHandler(track, event))) + 1;
                    String addMsg = FormatUtil.filter("Added **" + track.getInfo().title + "** (`" + TimeUtil.formatTime(track.getDuration()) + "`) "
                            + (pos == 0 ? "to begin playing" : " to the queue at position " + pos));
                    m.editMessage(addMsg).queue();
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    AudioTrack firstTrack = playlist.getSelectedTrack() != null ? playlist.getSelectedTrack() : playlist.getTracks().get(0);
                    trackLoaded(firstTrack);  // Play the first track
                }

                @Override
                public void noMatches() {
                    m.editMessage("No matches found for `" + trackUrl + "`.").queue();
                }

                @Override
                public void loadFailed(FriendlyException throwable) {
                    m.editMessage("Failed to load track: " + throwable.getMessage()).queue();
                }
            });
        });
    }



}
