package com.zhsan.gameobject;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.zhsan.common.GlobalVariables;
import com.zhsan.common.Point;
import com.zhsan.common.Utility;
import com.zhsan.common.exception.FileReadException;
import com.zhsan.common.exception.FileWriteException;
import com.zhsan.gamecomponents.GlobalStrings;
import com.zhsan.gamecomponents.common.XmlHelper;
import com.zhsan.lua.LuaAI;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by Peter on 24/5/2015.
 */
public class Architecture extends GameObject {

    public static final String SAVE_FILE = "Architecture.csv";

    private GameScenario scenario;

    private String name;
    private String nameImageName;

    private List<Point> location;

    private ArchitectureKind architectureKind;
    private Section belongedSection;

    private int population;
    private int fund, food;
    private float agriculture, commerce, technology, endurance, morale;

    private GameObjectList<MilitaryKind> creatableMilitaryKinds;

    private Architecture(int id) {
        super(id);
    }

    public static final GameObjectList<Architecture> fromCSV(FileHandle root, @NotNull GameScenario scen) {
        GameObjectList<Architecture> result = new GameObjectList<>();

        FileHandle f = root.child(Architecture.SAVE_FILE);
        try (CSVReader reader = new CSVReader(new InputStreamReader(f.read(), "UTF-8"))) {
            String[] line;
            int index = 0;
            while ((line = reader.readNext()) != null) {
                index++;
                if (index == 1) continue; // skip first line.

                Architecture data = new Architecture(Integer.parseInt(line[0]));
                data.nameImageName = line[1];
                data.name = line[2];
                data.architectureKind = scen.getArchitectureKinds().get(Integer.parseInt(line[3]));
                data.location = Point.fromCSVList(line[4]);
                data.belongedSection = scen.getSections().get(Integer.parseInt(line[5]));
                data.population = Integer.parseInt(line[6]);
                data.fund = Integer.parseInt(line[7]);
                data.food = Integer.parseInt(line[8]);
                data.agriculture = Float.parseFloat(line[9]);
                data.commerce = Float.parseFloat(line[10]);
                data.technology = Float.parseFloat(line[11]);
                data.morale = Float.parseFloat(line[12]);
                data.endurance = Float.parseFloat(line[13]);
                data.creatableMilitaryKinds = scen.getMilitaryKinds().getItemsFromCSV(line[14]);

                data.scenario = scen;

                result.add(data);
            }
        } catch (IOException e) {
            throw new FileReadException(f.path(), e);
        }

        return result;
    }

    public static final void toCSV(FileHandle root, GameObjectList<Architecture> data) {
        FileHandle f = root.child(SAVE_FILE);
        try (CSVWriter writer = new CSVWriter(f.writer(false, "UTF-8"))) {
            writer.writeNext(GlobalStrings.getString(GlobalStrings.Keys.ARCHITECTURE_SAVE_HEADER).split(","));
            for (Architecture d : data) {
                writer.writeNext(new String[]{
                        String.valueOf(d.getId()),
                        d.nameImageName,
                        d.getName(),
                        String.valueOf(d.architectureKind.getId()),
                        Point.toCSVList(d.location),
                        String.valueOf(d.belongedSection == null ? -1 : d.belongedSection.getId()),
                        String.valueOf(d.population),
                        String.valueOf(d.fund),
                        String.valueOf(d.food),
                        String.valueOf(d.agriculture),
                        String.valueOf(d.commerce),
                        String.valueOf(d.technology),
                        String.valueOf(d.endurance),
                        String.valueOf(d.morale),
                        d.creatableMilitaryKinds.toCSV()
                });
            }
        } catch (IOException e) {
            throw new FileWriteException(f.path(), e);
        }

    }

    @Override
    @LuaAI.ExportToLua
    public String getName() {
        return name;
    }

    public String getNameImageName() {
        return nameImageName;
    }

    public List<Point> getLocation() {
        return new ArrayList<>(location);
    }

    public ArchitectureKind getKind() {
        return architectureKind;
    }

    public Section getBelongedSection() {
        return belongedSection;
    }

    public Faction getBelongedFaction() {
        return belongedSection == null ? null : belongedSection.getBelongedFaction();
    }

    public GameObjectList<Person> getPersons() {
        return scenario.getPersons().filter(p -> p.getLocation() == this && p.getState() == Person.State.NORMAL);
    }

    public GameObjectList<Person> getUnhiredPersons() {
        return scenario.getPersons().filter(p -> p.getLocation() == this && p.getState() == Person.State.UNEMPLOYED);
    }

    public GameObjectList<Person> getPersonsExcludingMayor() {
        return scenario.getPersons().filter(p -> p.getLocation() == this && p.getState() == Person.State.NORMAL && p.getDoingWorkType() != Person.DoingWork.MAYOR);
    }

    public boolean hasFaction() {
        return getBelongedFaction() != null;
    }

    public GameObjectList<Facility> getFacilities() {
        return scenario.getFacilities().filter(f -> f.getBelongedArchitecture() == this);
    }

    public String getFactionName() {
        return this.getBelongedFaction() == null ? GlobalStrings.getString(GlobalStrings.Keys.NO_CONTENT) :
                this.getBelongedFaction().getName();
    }

    public String getArchitectureKindName() {
        return this.getKind().getName();
    }

    public int getPopulation() {
        return population;
    }

    public int getFund() {
        return fund;
    }

    public int getFood() {
        return food;
    }

    public String getFoodString() {
        return food / Integer.parseInt(GlobalStrings.getString(GlobalStrings.Keys.FOOD_UNIT)) +
                GlobalStrings.getString(GlobalStrings.Keys.FOOD_UNIT_STRING);
    }

    @LuaAI.ExportToLua
    public float getAgriculture() {
        return agriculture;
    }

    @LuaAI.ExportToLua
    public float getCommerce() {
        return commerce;
    }

    @LuaAI.ExportToLua
    public float getTechnology() {
        return technology;
    }

    @LuaAI.ExportToLua
    public float getEndurance() {
        return endurance;
    }

    @LuaAI.ExportToLua
    public float getMorale() {
        return morale;
    }

    Person pickMayor() {
        if (this.getBelongedFaction() != null && this.getPersons().contains(this.getBelongedFaction().getLeader())) {
            return this.getBelongedFaction().getLeader();
        }
        return this.getPersons().max((p, q) -> Integer.compare(p.getAbilitySum(), q.getAbilitySum()), null);
    }

    GameObjectList<Person> getMayorUnchecked() {
        return this.getPersons().filter(person -> person.getDoingWorkType() == Person.DoingWork.MAYOR);
    }

    public Person getMayor() {
        if (this.getPersons().size() == 0) return null;
        GameObjectList<Person> p = this.getPersons().filter(person -> person.getDoingWorkType() == Person.DoingWork.MAYOR);
        if (p.size() != 1) {
            throw new IllegalStateException("There should be one and only one mayor in every architecture");
        }
        return p.getFirst();
    }

    @LuaAI.ExportToLua
    public boolean canChangeMayor() {
        return this.getPersons().size() > 0 && this.getBelongedFaction() != null && this.getBelongedFaction().getLeader().getLocation() != this;
    }

    @LuaAI.ExportToLua
    public void changeMayor(int id) {
        changeMayor(scenario.getPerson(id));
    }

    public void changeMayor(Person newMayor) {
        if (newMayor.getLocation() != this) {
            throw new IllegalStateException("The new mayor must be in the architecture");
        }
        if (this.getBelongedFaction() == null) {
            throw new IllegalStateException("Empty architectures may not have mayors");
        }
        if (this.getBelongedFaction().getLeader().getLocation() == this) {
            throw new IllegalStateException("May not set mayor if the leader is in architecture");
        }
        newMayor.setDoingWork(Person.DoingWork.MAYOR);
    }

    public void addMayor(Person newMayor) {
        if (getMayorUnchecked().size() > 0) {
            throw new IllegalStateException("addMayor may only be used when there is no mayor at all");
        }
        if (newMayor.getLocation() != this) {
            throw new IllegalStateException("The new mayor must be in the architecture");
        }
        newMayor.setDoingWorkUnchecked(Person.DoingWork.MAYOR);
    }

    public GameObjectList<Person> getWorkingPersons(Predicate<Person.DoingWork> p) {
        return this.getPersons().filter(person -> p.test(person.getDoingWorkType()));
    }

    public GameObjectList<Person> getWorkingPersons(Person.DoingWork doingWork) {
        return getWorkingPersons(x -> x == doingWork);
    }

    public GameObjectList<Person> getAgriculturePersons() {
        return getWorkingPersons(Person.DoingWork.AGRICULTURE);
    }

    public GameObjectList<Person> getCommercePersons() {
        return getWorkingPersons(Person.DoingWork.COMMERCE);
    }

    public GameObjectList<Person> getTechnologyPersons() {
        return getWorkingPersons(Person.DoingWork.TECHNOLOGY);
    }

    public GameObjectList<Person> getEndurancePersons() {
        return getWorkingPersons(Person.DoingWork.ENDURANCE);
    }

    public GameObjectList<Person> getMoralePersons() {
        return getWorkingPersons(Person.DoingWork.MORALE);
    }

    public GameObjectList<Military> getMilitaries() {
        return scenario.getMilitaries().filter(x -> x.getLocation().get() == this);
    }

    public GameObjectList<MilitaryKind> getCreatableMilitaryKinds() {
        return creatableMilitaryKinds.asUnmodifiable();
    }

    public boolean createMilitary(MilitaryKind kind) {
        return scenario.createMilitary(this, kind);
    }

    public void advanceDay() {
        loseInternal();
        developInternal();
        if (scenario.getGameDate().getDayOfMonth() == 1) {
            gainResources();
        }
    }

    private void loseInternal() {
        this.agriculture = MathUtils.clamp(this.agriculture - GlobalVariables.internalDrop, 0, Float.MAX_VALUE);
        this.commerce = MathUtils.clamp(this.commerce - GlobalVariables.internalDrop, 0, Float.MAX_VALUE);
        this.technology = MathUtils.clamp(this.technology - GlobalVariables.internalDrop, 0, Float.MAX_VALUE);
        this.endurance = MathUtils.clamp(this.endurance - GlobalVariables.internalDrop, 0, Float.MAX_VALUE);
        this.morale = MathUtils.clamp(this.morale - GlobalVariables.internalDrop, 0, Float.MAX_VALUE);
    }

    private void developInternal() {
        Person mayor = this.getMayor();

        GameObjectList<Person> agricultureWorkingPersons = getWorkingPersons(Person.DoingWork.AGRICULTURE);
        GameObjectList<Person> commerceWorkingPersons = getWorkingPersons(Person.DoingWork.COMMERCE);
        GameObjectList<Person> technologyWorkingPersons = getWorkingPersons(Person.DoingWork.TECHNOLOGY);
        GameObjectList<Person> moraleWorkingPersons = getWorkingPersons(Person.DoingWork.MORALE);
        GameObjectList<Person> enduranceWorkingPersons = getWorkingPersons(Person.DoingWork.ENDURANCE);

        int totalWorkingPersons = (agricultureWorkingPersons.size() + commerceWorkingPersons.size() + technologyWorkingPersons.size() +
                moraleWorkingPersons.size() + enduranceWorkingPersons.size() + 1); // for extra mayor
        int totalCost = GlobalVariables.internalCost * totalWorkingPersons;

        if (totalCost > fund) {
            int affordable = fund / GlobalVariables.internalCost;
            if (affordable < 1) {
                return; // skip development entirely - mayor can't do work.
            }
            getWorkingPersons(x -> x != Person.DoingWork.NONE && x != Person.DoingWork.MAYOR)
                    .shuffledList().subList(0, totalWorkingPersons - affordable) // mayor must be working
                    .forEach(p -> p.setDoingWork(Person.DoingWork.NONE));

            agricultureWorkingPersons = getWorkingPersons(Person.DoingWork.AGRICULTURE);
            commerceWorkingPersons = getWorkingPersons(Person.DoingWork.COMMERCE);
            technologyWorkingPersons = getWorkingPersons(Person.DoingWork.TECHNOLOGY);
            moraleWorkingPersons = getWorkingPersons(Person.DoingWork.MORALE);
            enduranceWorkingPersons = getWorkingPersons(Person.DoingWork.ENDURANCE);

            totalCost = GlobalVariables.internalCost * affordable;
        }

        loseFund(totalCost);

        float agricultureAbility = (mayor == null ? 0 : mayor.getAgricultureAbility()) * GlobalVariables.mayorInternalWorkEfficiency +
                agricultureWorkingPersons.getAll().parallelStream()
                .map(p -> (float) p.getAgricultureAbility()).collect(Utility.diminishingSum(GlobalVariables.internalPersonDiminishingFactor));
        this.agriculture = Utility.diminishingGrowth(
                this.agriculture, agricultureAbility * GlobalVariables.internalGrowthFactor, this.getKind().getAgriculture());

        float commerceAbility = (mayor == null ? 0 : mayor.getCommerceAbility()) * GlobalVariables.mayorInternalWorkEfficiency +
                commerceWorkingPersons.getAll().parallelStream()
                        .map(p -> (float) p.getCommerceAbility()).collect(Utility.diminishingSum(GlobalVariables.internalPersonDiminishingFactor));
        this.commerce = Utility.diminishingGrowth(
                this.commerce, commerceAbility * GlobalVariables.internalGrowthFactor, this.getKind().getCommerce());

        float technologyAbility = (mayor == null ? 0 : mayor.getTechnologyAbility()) * GlobalVariables.mayorInternalWorkEfficiency +
                technologyWorkingPersons.getAll().parallelStream()
                        .map(p -> (float) p.getTechnologyAbility()).collect(Utility.diminishingSum(GlobalVariables.internalPersonDiminishingFactor));
        this.technology = Utility.diminishingGrowth(
                this.technology, technologyAbility * GlobalVariables.internalGrowthFactor, this.getKind().getTechnology());

        float moraleAbility = (mayor == null ? 0 : mayor.getMoraleAbility()) * GlobalVariables.mayorInternalWorkEfficiency +
                moraleWorkingPersons.getAll().parallelStream()
                        .map(p -> (float) p.getMoraleAbility()).collect(Utility.diminishingSum(GlobalVariables.internalPersonDiminishingFactor));
        this.morale = Utility.diminishingGrowth(
                this.morale, moraleAbility * GlobalVariables.internalGrowthFactor, this.getKind().getMorale());

        float enduranceAbility = (mayor == null ? 0 : mayor.getEnduranceAbility()) * GlobalVariables.mayorInternalWorkEfficiency +
                enduranceWorkingPersons.getAll().parallelStream()
                        .map(p -> (float) p.getEnduranceAbility()).collect(Utility.diminishingSum(GlobalVariables.internalPersonDiminishingFactor));
        this.endurance = Utility.diminishingGrowth(
                this.endurance, enduranceAbility * GlobalVariables.internalGrowthFactor, this.getKind().getEndurance());
    }

    private void gainResources() {
        this.fund = (int) MathUtils.clamp(this.fund +
                        GlobalVariables.gainFund * (this.commerce + this.population * GlobalVariables.gainFundPerPopulation),
                0, this.getKind().getMaxFood());
        this.food = (int) MathUtils.clamp(this.food +
                GlobalVariables.gainFood * (this.agriculture + this.population * GlobalVariables.gainFoodPerPopulation),
                0, this.getKind().getMaxFood());
    }

    public void loseFund(int x) {
        fund = Math.max(fund - x, 0);
    }

}
