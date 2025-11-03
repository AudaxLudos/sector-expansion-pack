package sectorexpansionpack;

public class MissionScenarioSpec {
    protected String missionId;
    protected String scenarioId;

    protected float frequency;
    protected float duration;

    protected Integer minCreditReward;
    protected Integer maxCreditReward;

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

    public float getDuration() {
        return this.duration;
    }

    public void setDuration(float duration) {
        this.duration = duration;
    }

    public Integer getMinCreditReward() {
        return this.minCreditReward;
    }

    public void setMinCreditReward(Integer minCreditReward) {
        this.minCreditReward = minCreditReward;
    }

    public Integer getMaxCreditReward() {
        return this.maxCreditReward;
    }

    public void setMaxCreditReward(Integer maxCreditReward) {
        this.maxCreditReward = maxCreditReward;
    }
}
