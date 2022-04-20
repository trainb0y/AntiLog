package io.github.trainb0y1.antilog

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
	val deadPlayers = mutableMapOf<UUID, UUID?>()

	override fun onEnable() {
		server.pluginManager.registerEvents(this, this)
	}

	@EventHandler
	fun onPlayerLogOut(event: PlayerQuitEvent) {
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
		if (!deadPlayers.containsKey(event.player.uniqueId)) return
		event.player.sendMessage("While you were offline you were killed by " +
				server.getOfflinePlayer(deadPlayers[event.player.uniqueId]!!).name)
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
	}
}