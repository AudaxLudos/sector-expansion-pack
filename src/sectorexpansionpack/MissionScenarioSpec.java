package sectorexpansionpack;

public class MissionScenarioSpec {
    protected String missionId;
    protected String scenarioId;

    protected float frequency;

    public String getMissionId() {
        return this.missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public String getScenarioId() {
        return this.scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public float getFrequency() {
        return this.frequency;
    }

    public void setFrequency(float frequency) {
        this.frequency = frequency;
    }
}
