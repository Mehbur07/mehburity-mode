package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.MehburityMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide source of truth for "who owns which Mehburity and which box", saved with the overworld.
 * Entities check themselves against it, so a copy that was left in an unloaded chunk (after a crash or a
 * re-summon) removes itself as soon as it loads. That keeps the one-Mehburity-per-player rule.
 */
public final class CompanionRegistry {
	/** One player's Mehburity. {@code stowed} means it travelled with the owner when they logged out. */
	public record Companion(UUID entityId, CompanionMode mode, boolean stowed) {
		static final Codec<Companion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.STRING_CODEC.fieldOf("entity").forGetter(Companion::entityId),
				CompanionMode.CODEC.optionalFieldOf("mode", CompanionMode.FOLLOW).forGetter(Companion::mode),
				Codec.BOOL.optionalFieldOf("stowed", false).forGetter(Companion::stowed)
		).apply(instance, Companion::new));

		public Companion withMode(CompanionMode newMode) {
			return new Companion(entityId, newMode, stowed);
		}

		public Companion withStowed(boolean newStowed) {
			return new Companion(entityId, mode, newStowed);
		}
	}

	record Data(Map<UUID, Companion> companions, Map<UUID, UUID> boxes) {
		static final Data EMPTY = new Data(Map.of(), Map.of());
		static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Companion.CODEC).optionalFieldOf("companions", Map.of())
						.forGetter(Data::companions),
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC).optionalFieldOf("boxes", Map.of())
						.forGetter(Data::boxes)
		).apply(instance, Data::new));
	}

	private static final AttachmentType<Data> DATA = AttachmentRegistry.create(MehburityMod.id("companions"),
			builder -> builder.persistent(Data.CODEC).initializer(() -> Data.EMPTY));

	private CompanionRegistry() {
	}

	/** Forces class loading so the attachment type is registered during mod init. */
	public static void register() {
	}

	private static Data data(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(DATA);
	}

	private static void store(MinecraftServer server, Data data) {
		server.overworld().setAttached(DATA, data);
	}

	public static @Nullable Companion companion(MinecraftServer server, UUID owner) {
		return data(server).companions().get(owner);
	}

	public static void putCompanion(MinecraftServer server, UUID owner, Companion companion) {
		Data data = data(server);
		Map<UUID, Companion> companions = new HashMap<>(data.companions());
		companions.put(owner, companion);
		Map<UUID, UUID> boxes = new HashMap<>(data.boxes());
		boxes.remove(owner); // a player with a Mehburity has no box waiting
		store(server, new Data(Map.copyOf(companions), Map.copyOf(boxes)));
	}

	public static @Nullable UUID box(MinecraftServer server, UUID owner) {
		return data(server).boxes().get(owner);
	}

	public static void putBox(MinecraftServer server, UUID owner, UUID boxId) {
		Data data = data(server);
		Map<UUID, UUID> boxes = new HashMap<>(data.boxes());
		boxes.put(owner, boxId);
		store(server, new Data(data.companions(), Map.copyOf(boxes)));
	}

	/** True once the player got a box or a Mehburity, so the first-join box is given only once. */
	public static boolean hasEverReceived(MinecraftServer server, UUID owner) {
		Data data = data(server);
		return data.companions().containsKey(owner) || data.boxes().containsKey(owner);
	}

	public static boolean isCurrentCompanion(ServerLevel level, UUID owner, UUID entityId) {
		Companion companion = companion(level.getServer(), owner);
		return companion != null && companion.entityId().equals(entityId);
	}

	public static boolean isCurrentBox(ServerLevel level, UUID owner, UUID boxId) {
		return boxId.equals(box(level.getServer(), owner));
	}
}
