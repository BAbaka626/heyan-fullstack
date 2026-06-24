package work.xy0712.xz

import org.junit.Assert.assertEquals
import org.junit.Test
import work.xy0712.xz.data.model.EnterpriseSummary
import work.xy0712.xz.ui.enterprise.EnterpriseRiskLevel
import work.xy0712.xz.ui.enterprise.getEnterpriseRiskLevel

class EnterpriseRiskUtilsTest {
    @Test
    fun qyfxdjThreeMapsToMediumRisk() {
        val enterprise = EnterpriseSummary(name = "测试企业", qyfxdj = 3)

        assertEquals(EnterpriseRiskLevel.MEDIUM, getEnterpriseRiskLevel(enterprise))
    }

    @Test
    fun missingRiskFieldsRemainUnknown() {
        assertEquals(EnterpriseRiskLevel.UNKNOWN, getEnterpriseRiskLevel(EnterpriseSummary()))
    }

    @Test
    fun computedRiskTakesPriorityOverQyfxdj() {
        val enterprise = EnterpriseSummary(computedRiskLevel = "HIGH", qyfxdj = 4)

        assertEquals(EnterpriseRiskLevel.HIGH, getEnterpriseRiskLevel(enterprise))
    }
}
