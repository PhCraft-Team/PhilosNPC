package com.phcraft.philosnpc;

import com.phcraft.philosnpc.npc.NPCManager;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkListener implements Listener {

    private final NPCManager npcManager;

    public ChunkListener(NPCManager npcManager) {
        this.npcManager = npcManager;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        // 区块加载时，如果区块内有NPC，重新生成实体
        npcManager.respawnNPCsInChunk(chunk);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        // 区块卸载时不删除实体数据，只移除实体（数据保留在内存）
        npcManager.despawnNPCsInChunk(chunk);
    }
}
