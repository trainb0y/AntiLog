package io.github.trainb0y1.antilog

import org.bukkit.GameMode
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class AntiLog: JavaPlugin(), Listener {
	val stands = mutableMapOf<ArmorStand, Pair<UUID, PlayerInventory>>()

	// Dead player to killer
	val deadPlayers = mutableMapOf<UUID, UUID?>()

	override fun onEnable() {
		server.pluginManager.registerEvents(this, this)
	}

	@EventHandler
	fun onPlayerLogOut(event: PlayerQuitEvent) {
		if (event.player.gameMode != GameMode.SURVIVAL) return
		// Have to store the inventory as it isn't accessible in OfflinePlayer
		val stand = event.player.world.spawn(event.player.location, ArmorStand::class.java)
		stands[stand] = Pair(event.player.uniqueId, event.player.inventory)

		server.scheduler.scheduleSyncDelayedTask(this,
			Runnable {
				// Confirm that it still exists
				stands[stand] ?: return@Runnable
				stands.remove(stand)
				stand.health = 0.0
			}, 1200 // 1 minute
		)
	}

	@EventHandler
	fun onPlayerLogIn(event: PlayerJoinEvent) {
		// If the player has an armor stand, despawn it
		// Note this would only remove one, if somehow they got multiple that would be an issue
		stands.remove(stands.filter { it.value.first == event.player.uniqueId }.keys.first())

		if (!deadPlayers.containsKey(event.player.uniqueId)) return

		// Kill the player, send a message, and clear their inventory
		val killer = run {server.getOfflinePlayer(deadPlayers[event.player.uniqueId] ?: return@run null)}
		event.player.sendMessage("While you were offline you were killed by ${killer?.name}")
		deadPlayers.remove(event.player.uniqueId)
		event.player.inventory.clear()
		event.player.health = 0.0
	}

	@EventHandler
	fun onArmorStandDie(event: EntityDeathEvent) {
		val stand = event.entity as? ArmorStand ?: return
		val player = this.server.getOfflinePlayer(stands[stand]?.first ?: return)
		val inventory = stands[stand]!!.second
		val location = event.entity.location
		inventory.filter{!(it?.containsEnchantment(Enchantment.VANISHING_CURSE) ?: true)}.forEach {
			location.world.dropItem(location, it)
		}
		stands.remove(stand)
		deadPlayers[player.uniqueId] = stand.killer?.uniqueId
		event.drops.clear() // don't want to drop the stand
	}
}