package com.pse.pse.models

import com.pse.pse.models.TeamLevelModel

data class TeamLevelStatus(
    val teamLevel: TeamLevelModel,  // Original TeamLevelModel
    var levelUnlocked: Boolean
) {
    // No-argument constructor is implicitly generated for data classes,
    // but you can define it explicitly if needed.
    constructor() : this(TeamLevelModel(), false)  // Explicit no-argument constructor
}
