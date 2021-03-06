package ru.rdude.rpg.game.logic.stats.secondary;


import com.fasterxml.jackson.annotation.JsonIdentityReference;
import ru.rdude.rpg.game.logic.stats.Calculatable;
import ru.rdude.rpg.game.logic.stats.Stat;
import ru.rdude.rpg.game.logic.stats.primary.*;
import ru.rdude.rpg.game.utils.jsonextension.JsonPolymorphicSubType;

@JsonPolymorphicSubType("physicResistance")
public class PhysicResistance extends Stat implements Calculatable {

    private boolean calculatable;
    @JsonIdentityReference(alwaysAsId = true)
    private Int intel;
    @JsonIdentityReference(alwaysAsId = true)
    private Luck luck;
    @JsonIdentityReference(alwaysAsId = true)
    private Str str;
    @JsonIdentityReference(alwaysAsId = true)
    private Dex dex;
    @JsonIdentityReference(alwaysAsId = true)
    private Agi agi;
    @JsonIdentityReference(alwaysAsId = true)
    private Vit vit;
    @JsonIdentityReference(alwaysAsId = true)
    private Lvl lvl;

    private PhysicResistance() { }

    public PhysicResistance(double value) {
        super(value);
        this.calculatable = false;
    }

    @Override
    public String getName() {
        return "Physic resistance";
    }

    public PhysicResistance(double value, Int intel, Luck luck, Str str, Dex dex, Agi agi, Vit vit, Lvl lvl) {
        super(value);
        this.calculatable = false;
        this.intel = intel;
        this.luck = luck;
        this.str = str;
        this.dex = dex;
        this.agi = agi;
        this.vit = vit;
        this.lvl = lvl;
        intel.subscribe(this);
        luck.subscribe(this);
        str.subscribe(this);
        dex.subscribe(this);
        agi.subscribe(this);
        vit.subscribe(this);
        lvl.subscribe(this);
    }

    @Override
    public void setCalculatable(boolean calculatable) {
        this.calculatable = calculatable;
    }

    @Override
    public double calculate() {
        if (!calculatable) return value();
        double INT = intel.value();
        double LVL = lvl.value();
        double LUCK = luck.value();
        double STR = str.value();
        double DEX = dex.value();
        double AGI = agi.value();
        double VIT = vit.value();
        set(VIT*0.2 + Math.floor(VIT/3)*0.2 + Math.floor(VIT/4)*0.1 + LVL*0.1 + LUCK*0.05 + Math.floor(STR/7 + DEX/7 + AGI/7 + INT/7)*0.1);
        return value();
    }
}
