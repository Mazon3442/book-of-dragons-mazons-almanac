package local.bodaddon;

import local.bodaddon.eel.EelEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, BodAddon.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<EelEntity>> EEL = ENTITIES.register("eel",
        () -> EntityType.Builder.of(EelEntity::new, MobCategory.WATER_AMBIENT).sized(0.5F, 0.3F).eyeHeight(0.2F)
            .clientTrackingRange(4).build("eel"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(ModEntities::attributes);
        modBus.addListener(ModEntities::spawnPlacements);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(EEL.get(), EelEntity.createAttributes().build());
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(EEL.get(), SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR,
            WaterAnimal::checkSurfaceWaterAnimalSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModEntities() {}
}
