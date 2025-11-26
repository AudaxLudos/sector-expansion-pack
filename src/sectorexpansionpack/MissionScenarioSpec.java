package sectorexpansionpack;

import java.util.ArrayList;
import java.util.List;

public class MissionScenarioSpec {
    protected String missionId;
    protected String scenarioId;

    protected float frequency;
    protected float duration;

    protected String creditReward;

    protected String type;

    protected List<String> complications = new ArrayList<>();
    protected List<String> tags = new ArrayList<>();

    protected String data1;
    protected String data2;
    protected String data3;

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

    public String getCreditReward() {
        return this.creditReward;
    }

    public void setCreditReward(String creditReward) {
        this.creditReward = creditReward;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getComplications() {
        return new ArrayList<>(this.complications);
    }

    public void setComplications(List<String> complications) {
        this.complications = complications;
    }

    public List<String> getTags() {
        return new ArrayList<>(this.tags);
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getData1() {
        return this.data1;
    }

    public void setData1(String data1) {
        this.data1 = data1;
    }

    public String getData2() {
        return this.data2;
    }

    public void setData2(String data2) {
        this.data2 = data2;
    }

    public String getData3() {
        return this.data3;
    }

    public void setData3(String data3) {
        this.data3 = data3;
    }
}
