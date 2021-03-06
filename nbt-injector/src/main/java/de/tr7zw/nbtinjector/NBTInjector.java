package de.tr7zw.nbtinjector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import de.tr7zw.changeme.nbtapi.ClassWrapper;
import de.tr7zw.changeme.nbtapi.NBTCompound;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.NBTEntity;
import de.tr7zw.changeme.nbtapi.NBTReflectionUtil;
import de.tr7zw.changeme.nbtapi.NBTTileEntity;
import de.tr7zw.changeme.nbtapi.NbtApiException;
import de.tr7zw.changeme.nbtapi.ObjectCreator;
import de.tr7zw.changeme.nbtapi.ReflectionMethod;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import javassist.ClassPool;

public class NBTInjector {

	static Logger logger = Logger.getLogger("NBTInjector");

	/**
	 * Replaces the vanilla classes with Wrapped classes that support custom NBT.
	 * This method needs to be called during onLoad so classes are replaced before worlds load.
	 * If your plugin adds a new Entity(probably during onLoad) recall this method so it's class gets Wrapped.
	 */
	public static void inject() {
		try {
			ClassPool classPool = ClassPool.getDefault();
			logger.info("[NBTINJECTOR] Injecting Entity classes...");
			if(MinecraftVersion.getVersion().getVersionId() <= MinecraftVersion.MC1_10_R1.getVersionId()) {
				for (Map.Entry<String, Class<?>> entry : new HashSet<>(Entity.getCMap().entrySet())) {
					try {
						if (INBTWrapper.class.isAssignableFrom(entry.getValue())) { continue; }//Already injected
						int entityId = Entity.getFMap().get(entry.getValue());

						Class<?> wrapped = ClassGenerator.wrapEntity(classPool, entry.getValue(), "__extraData");
						Entity.getCMap().put(entry.getKey(), wrapped);
						Entity.getDMap().put(wrapped, entry.getKey());

						Entity.getEMap().put(entityId, wrapped);
						Entity.getFMap().put(wrapped, entityId);
					} catch (Exception e) {
						throw new RuntimeException("Exception while injecting " + entry.getKey(), e);
					}
				}
			} else {
				Object registry = Entity.getRegistry();
				Map<Object, Object> inverse = new HashMap<>();
				Set<?> it = new HashSet<>((Set<?>) ReflectionMethod.REGISTRY_KEYSET.run(registry));
				for(Object mckey : it) {
					Class<?> tileclass = (Class<?>) ReflectionMethod.REGISTRY_GET.run(registry, mckey);
					inverse.put(tileclass, mckey);
					try {
						if (INBTWrapper.class.isAssignableFrom(tileclass)) { continue; }//Already injected
						Class<?> wrapped = ClassGenerator.wrapEntity(classPool, tileclass, "__extraData");
						ReflectionMethod.REGISTRY_SET.run(registry, mckey, wrapped);
						inverse.put(wrapped, mckey);
					} catch (Exception e) {
						throw new RuntimeException("Exception while injecting " + mckey, e);
					}
				}
				Field inverseField = registry.getClass().getDeclaredField("b");
				setFinal(registry, inverseField, inverse);
			}

			logger.info("[NBTINJECTOR] Injecting Tile Entity classes...");
			if(MinecraftVersion.getVersion().getVersionId() <= MinecraftVersion.MC1_10_R1.getVersionId()) {
				for (Map.Entry<String, Class<?>> entry : new HashSet<>(TileEntity.getFMap().entrySet())) {
					try {
						if (INBTWrapper.class.isAssignableFrom(entry.getValue())) { continue; }//Already injected
						Class<?> wrapped = ClassGenerator.wrapTileEntity(classPool, entry.getValue(), "__extraData");
						TileEntity.getFMap().put(entry.getKey(), wrapped);
						TileEntity.getGMap().put(wrapped, entry.getKey());
					} catch (Exception e) {
						throw new RuntimeException("Exception while injecting " + entry.getKey(), e);
					}
				}
			} else {
				Object registry = TileEntity.getRegistry();
				Map<Object, Object> inverse = new HashMap<>();
				Set<?> it = new HashSet<>((Set<?>) ReflectionMethod.REGISTRY_KEYSET.run(registry));
				for(Object mckey : it) {
					Class<?> tileclass = (Class<?>) ReflectionMethod.REGISTRY_GET.run(registry, mckey);
					inverse.put(tileclass, mckey);
					try {
						if (INBTWrapper.class.isAssignableFrom(tileclass)) { continue; }//Already injected
						Class<?> wrapped = ClassGenerator.wrapTileEntity(classPool, tileclass, "__extraData");
						ReflectionMethod.REGISTRY_SET.run(registry, mckey, wrapped);
						inverse.put(wrapped, mckey);
					} catch (Exception e) {
						throw new RuntimeException("Exception while injecting " + mckey, e);
					}
				}
				Field inverseField = registry.getClass().getDeclaredField("b");
				setFinal(registry, inverseField, inverse);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static NBTCompound getNbtData(Object object) {
		if (object instanceof INBTWrapper) {
			return ((INBTWrapper) object).getNbtData();
		}
		return null;
	}

	/**
	 * Entities that have just been spawned(from plugins or natually) may use the wrong(Vanilla) class.
	 * Calling this method removes the wrong entity and respawns it using the correct class. It also tries
	 * to keep all data of the original entity, but some stuff like passengers will probably cause problems.
	 * Recalling this method on a patched Entity doesn nothing and returns the Entity instance.
	 * 
	 * WARNING: This causes the entity to get a new Bukkit Entity instance. For other plugins the entity will
	 * be a dead/removed entity, even if it's still kinda there. Bestcase you spawn an Entity and directly replace
	 * your instance with a patched one.
	 * Also, for players ingame the entity will quickly flash, since it's respawned.
	 * 
	 * @param entity Entity to respawn with the correct class.
	 * @return Entity The new instance of the entity.
	 */
	public static org.bukkit.entity.Entity patchEntity(org.bukkit.entity.Entity entity){
		if (entity == null) { return null; }
		try {
			Object ent = NBTReflectionUtil.getNMSEntity(entity);
			if (!(ent instanceof INBTWrapper)) {//Replace Entity with custom one
				Object cworld = ClassWrapper.CRAFT_WORLD.getClazz().cast(entity.getWorld());
				Object nmsworld = ReflectionMethod.CRAFT_WORLD_GET_HANDLE.run(cworld);
				NBTContainer oldNBT = new NBTContainer(new NBTEntity(entity).getCompound());
				Method create = ClassWrapper.NMS_ENTITYTYPES.getClazz().getMethod("a", ClassWrapper.NMS_NBTTAGCOMPOUND.getClazz(), ClassWrapper.NMS_WORLD.getClazz());
				String id = "";
				if(MinecraftVersion.getVersion().getVersionId() <= MinecraftVersion.MC1_10_R1.getVersionId()) {
					id = Entity.getBackupMap().get(ent.getClass());
				}else {
					id = ReflectionMethod.REGISTRY_GET_INVERSE.run(Entity.getRegistry(), ent.getClass()).toString();
				}
				oldNBT.setString("id", id);
				oldNBT.removeKey("UUIDMost");
				oldNBT.removeKey("UUIDLeast");
				entity.remove();
				Object newEntity = create.invoke(null, oldNBT.getCompound(), nmsworld);
				Method spawn = ClassWrapper.NMS_WORLD.getClazz().getMethod("addEntity", ClassWrapper.NMS_ENTITY.getClazz());
				spawn.invoke(nmsworld, newEntity);
				Method asBukkit = newEntity.getClass().getMethod("getBukkitEntity");
				return (org.bukkit.entity.Entity) asBukkit.invoke(newEntity);
			}
		} catch (Exception e) {
			throw new NbtApiException("Error while patching an Entity '" + entity + "'", e);
		}
		return entity;
	}

	/**
	 * Gets the persistant NBTCompound from a given entity. If the Entity isn't yet patched,
	 * this method will return null.
	 * 
	 * @param entity Entity to get the NBTCompound from
	 * @return NBTCompound instance
	 */
	public static NBTCompound getNbtData(org.bukkit.entity.Entity entity) {
		if (entity == null) { return null; }
		try {
			Object ent = NBTReflectionUtil.getNMSEntity(entity);
			/*if (!(ent instanceof INBTWrapper)) {//Replace Entity with custom one
				entity = patchEntity(entity);
				System.out.println("Autopatched Entity: " + entity);
	            return getNbtData(NBTReflectionUtil.getNMSEntity(entity));
				return null; // For now don't do anything, just return null.
			}*/
			return getNbtData(ent);
		} catch (Exception e) {
			throw new NbtApiException("Error while getting the NBT from an Entity '" + entity + "'.", e);
		}
	}

	/**
	 * Gets the persistant NBTCompound from a given TileEntity. If the Tile isn't yet patched,
	 * this method will autopatch it. This will unlink the given BlockState, so calling block.getState()
	 * again may be necessary. This behavior may change in the future.
	 * 
	 * @param tile TileEntity to get the NBTCompound from
	 * @return NBTCompound instance
	 */
	public static NBTCompound getNbtData(org.bukkit.block.BlockState tile) {
		if (tile == null) { return null; }
		try {
			Object pos = ObjectCreator.NMS_BLOCKPOSITION.getInstance(tile.getX(), tile.getY(), tile.getZ());
			Object cworld = ClassWrapper.CRAFT_WORLD.getClazz().cast(tile.getWorld());
			Object nmsworld = ReflectionMethod.CRAFT_WORLD_GET_HANDLE.run(cworld);
			Object tileEntity = ReflectionMethod.NMS_WORLD_GET_TILEENTITY.run(nmsworld, pos);
			if(tileEntity == null) { // Not a tile block
				return null;
			}
			if (!(tileEntity instanceof INBTWrapper)) {
				//Loading Updated Tile

				Object tileEntityUpdated;
				if(MinecraftVersion.getVersion() == MinecraftVersion.MC1_9_R1) {
					tileEntityUpdated = ReflectionMethod.TILEENTITY_LOAD_LEGACY191.run(null, null, new NBTTileEntity(tile).getCompound());
				} else if(MinecraftVersion.getVersion() == MinecraftVersion.MC1_8_R3 || MinecraftVersion.getVersion() == MinecraftVersion.MC1_9_R2) {
					tileEntityUpdated = ReflectionMethod.TILEENTITY_LOAD_LEGACY183.run(null, new NBTTileEntity(tile).getCompound());
				} else {
					tileEntityUpdated = ReflectionMethod.TILEENTITY_LOAD.run(null, nmsworld, new NBTTileEntity(tile).getCompound());
				}
				ReflectionMethod.NMS_WORLD_REMOVE_TILEENTITY.run(nmsworld, pos);
				ReflectionMethod.NMS_WORLD_SET_TILEENTITY.run(nmsworld, pos, tileEntityUpdated);
				return getNbtData(tileEntityUpdated);
			}
			return getNbtData(tileEntity);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void setFinal(Object obj, Field field, Object newValue) throws Exception {
		field.setAccessible(true);

		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

		field.set(obj, newValue);
	}

	private static Field getAccessable(Field field) {
		field.setAccessible(true);
		return field;
	}

	static class Entity {
		private static Map<Class<?>, String> backupMap = new HashMap<>();

		static {
			try {
				if(MinecraftVersion.getVersion().getVersionId() <= MinecraftVersion.MC1_10_R1.getVersionId()) {
					backupMap.putAll(getDMap());
				}
			} catch (ReflectiveOperationException e) {
				e.printStackTrace();
			}
		}

		static Object getRegistry() throws ReflectiveOperationException {
			return getAccessable(ClassWrapper.NMS_ENTITYTYPES.getClazz().getDeclaredField("b")).get(null);
		}

		static Map<Class<?>, String> getBackupMap() throws ReflectiveOperationException {
			return backupMap;
		}

		static Map<String, Class<?>> getCMap() throws ReflectiveOperationException {
			return (Map<String, Class<?>>) getAccessable(ClassWrapper.NMS_ENTITYTYPES.getClazz().getDeclaredField("c")).get(null);
		}

		static Map<Class<?>, String> getDMap() throws ReflectiveOperationException {
			return (Map<Class<?>, String>) getAccessable(ClassWrapper.NMS_ENTITYTYPES.getClazz().getDeclaredField("d")).get(null);
		}

		static Map<Integer, Class<?>> getEMap() throws ReflectiveOperationException {
			return (Map<Integer, Class<?>>) getAccessable(ClassWrapper.NMS_ENTITYTYPES.getClazz().getDeclaredField("e")).get(null);
		}

		static Map<Class<?>, Integer> getFMap() throws ReflectiveOperationException {
			return (Map<Class<?>, Integer>) getAccessable(ClassWrapper.NMS_ENTITYTYPES.getClazz().getDeclaredField("f")).get(null);
		}
	}

	static class TileEntity {

		static Object getRegistry() throws ReflectiveOperationException {
			return getAccessable(ClassWrapper.NMS_TILEENTITY.getClazz().getDeclaredField("f")).get(null);
		}

		static Map<String, Class<?>> getFMap() throws ReflectiveOperationException {
			return (Map<String, Class<?>>) getAccessable(ClassWrapper.NMS_TILEENTITY.getClazz().getDeclaredField("f")).get(null);
		}

		static Map<Class<?>, String> getGMap() throws ReflectiveOperationException {
			return (Map<Class<?>, String>) getAccessable(ClassWrapper.NMS_TILEENTITY.getClazz().getDeclaredField("g")).get(null);
		}
	}
}
