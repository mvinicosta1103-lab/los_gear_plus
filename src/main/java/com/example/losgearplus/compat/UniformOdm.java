package com.example.losgearplus.compat;

import com.example.losgearplus.grip.HolsterWeapons;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * New ODM Uniform (los_gear:new_odm_uniform) como Anti-Personnel ODM.
 *
 * <h3>Por que o uniforme não funcionava</h3>
 * O DAOT 2.5.x lê o ODM SEMPRE por {@code daot.OdmHarness.getGear(LivingEntity)}: o gear que está DENTRO do
 * ODM Harness (slot de pernas). O uniforme é um peitoral, então esse método devolve EMPTY e o DAOT acha que
 * o jogador não tem ODM (sem hooks, sem boost, sem gás). O los_gear r315 até faz o uniforme "virar o gear"
 * nesse método, mas só quando NÃO há harness vestido (e com descritor intermediary, que nem aplica no
 * runClient de desenvolvimento). Com o harness vestido e o slot de gear vazio, nada acontecia.
 *
 * <h3>O que isto faz</h3>
 * {@link #resolve} é chamado pelo mixin no retorno de {@code OdmHarness.getGear(LivingEntity)}:
 * se o DAOT não achou gear nenhum, o jogador veste o uniforme no peito e (opcionalmente) está com Automatic
 * Pistol nas mãos, devolvemos a PRÓPRIA pilha do uniforme como gear. Como é a mesma instância do peitoral:
 * <ul>
 *   <li>o gás (1000) é lido/gravado nela pelos ganchos do los_gear (OdmGasLink / setGasOnGear);</li>
 *   <li>{@code isODMGear}/{@code isAPG}/{@code isValidGripForLeggings} já reconhecem o uniforme (los_gear),
 *       inclusive a pistola como arma válida; logo hooks, ODMG Speed, boost, HUD de gás e o resto do DAOT
 *       passam a funcionar, no cliente e no servidor (o método é código comum).</li>
 * </ul>
 * Se houver um gear de verdade no harness (New ODM Gear, ODM Gear, APG), ele continua mandando.
 */
public final class UniformOdm {
    private UniformOdm() {}

    /**
     * true  = o uniforme só vira ODM com Automatic Pistol em uma das mãos (ou um Gas Canister, para o
     *         reabastecimento com Shift + botão direito continuar funcionando);
     * false = o uniforme é ODM sempre que estiver vestido (como o los_gear faz sem harness).
     */
    public static final boolean REQUIRE_PISTOLS = true;

    private static final ResourceLocation UNIFORM_ID = ResourceLocation.fromNamespaceAndPath("los_gear", "new_odm_uniform");
    private static final ResourceLocation CANISTER_ID = ResourceLocation.fromNamespaceAndPath("dannys-aot", "gas_canister");

    private static Item uniformItem;
    private static Item canisterItem;

    /**
     * @param entity quem está sendo consultado
     * @param real   o que o DAOT achou (EMPTY se não há gear no harness)
     * @return o gear a usar: {@code real} se houver; senão o uniforme vestido (quando aplicável); senão {@code real}
     */
    public static ItemStack resolve(LivingEntity entity, ItemStack real) {
        if (real == null || !real.isEmpty()) return real;
        if (!(entity instanceof Player player)) return real;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty() || chest.getItem() != uniform()) return real;
        if (REQUIRE_PISTOLS && !holdsPistolOrCanister(player)) return real;
        return chest;
    }

    private static boolean holdsPistolOrCanister(Player player) {
        return isPistolOrCanister(player.getMainHandItem()) || isPistolOrCanister(player.getOffhandItem());
    }

    private static boolean isPistolOrCanister(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (HolsterWeapons.isPistol(stack)) return true;
        Item canister = canister();
        return canister != null && stack.getItem() == canister;
    }

    /** Item do uniforme; null enquanto o los_gear não registrou (nunca fixa um valor antes disso). */
    private static Item uniform() {
        Item item = uniformItem;
        if (item == null && BuiltInRegistries.ITEM.containsKey(UNIFORM_ID)) {
            item = BuiltInRegistries.ITEM.get(UNIFORM_ID);
            uniformItem = item;
        }
        return item;
    }

    private static Item canister() {
        Item item = canisterItem;
        if (item == null && BuiltInRegistries.ITEM.containsKey(CANISTER_ID)) {
            item = BuiltInRegistries.ITEM.get(CANISTER_ID);
            canisterItem = item;
        }
        return item;
    }
}