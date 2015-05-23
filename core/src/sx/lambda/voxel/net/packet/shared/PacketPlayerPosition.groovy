package sx.lambda.voxel.net.packet.shared

import groovy.transform.CompileStatic
import io.netty.channel.ChannelHandlerContext
import sx.lambda.voxel.VoxelGameClient
import sx.lambda.voxel.entity.EntityPosition
import sx.lambda.voxel.entity.player.Player
import sx.lambda.voxel.net.packet.SharedPacket
import sx.lambda.voxel.net.packet.server.*
import sx.lambda.voxel.server.VoxelGameServer
import sx.lambda.voxel.server.net.ConnectedClient
import sx.lambda.voxel.world.chunk.IChunk

@CompileStatic
class PacketPlayerPosition implements SharedPacket {

    private float x, y, z

    public PacketPlayerPosition(EntityPosition pos) {
        this(pos.x, pos.y, pos.z)
    }

    public PacketPlayerPosition(float x, float y, float z) {
        this.x = x
        this.y = y
        this.z = z
    }

    @Override
    //TODO Right now we're going to trust the client, but we definitely shouldn't in the future
    void handleServerReceive(VoxelGameServer server, ChannelHandlerContext ctx) {
        ConnectedClient cc = server.getClient(ctx);
        Player p = cc.player;

        if (p != null) {
            p.getPosition().setPos(x, y, z)

            for (ConnectedClient client : server.clientList) {
                if (client.context != ctx)
                    client.context.writeAndFlush(new PacketEntityPosition(p))
            }

            if (cc.lastChunkSendPos == null) {
                sendChunks(server, ctx, p, server.config.viewDistance)
            } else {
                if (cc.info == null) {
                    if (cc.lastChunkSendPos.planeDistance(p.getPosition()) >= ((server.config.viewDistance / 2f) * server.getWorld().getChunkSize()))
                        sendChunks(server, ctx, p, server.config.viewDistance)
                } else {
                    int viewDistance = Math.min(server.config.viewDistance, cc.info.viewDistance);
                    if (cc.lastChunkSendPos.planeDistance(p.getPosition()) >= ((viewDistance / 2f) * server.getWorld().getChunkSize()))
                        sendChunks(server, ctx, p, viewDistance)
                }
            }

        } else {
            ctx.writeAndFlush(new PacketKick("Sent position before ready"))
            ctx.disconnect()
            server.rmClient(ctx)
        }
    }

    @Override
    void handleClientReceive(ChannelHandlerContext ctx) {
        VoxelGameClient.instance.getPlayer().getPosition().setPos(x, y, z)
    }

    private void sendChunks(VoxelGameServer server, ChannelHandlerContext ctx, Player p, int viewDistance) {
        ConnectedClient client = server.getClient(ctx)
        IChunk[] chunkList = server.getWorld().getChunksInRange(p.getPosition(), viewDistance)
        ctx.writeAndFlush(new PacketStartChunkGroup())
        for (IChunk c : chunkList) {
            if (!client.hadChunks.contains(c)) {
                ctx.writeAndFlush(new PacketChunkData(c))
                client.hadChunks.add(c)
            }
        }
        ctx.writeAndFlush(new PacketEndChunkGroup())
        server.getClient(ctx).lastChunkSendPos = p.getPosition().clone()
    }

}