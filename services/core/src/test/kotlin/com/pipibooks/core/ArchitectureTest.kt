package com.pipibooks.core

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

@AnalyzeClasses(packages = ["com.pipibooks.core"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {
    // core 모듈(member, card, feed, library, stats, admin) 사이에 순환 의존이 없어야 한다.
    @ArchTest
    val modulesAreFreeOfCycles: ArchRule =
        slices()
            .matching("com.pipibooks.core.(*)..")
            .should()
            .beFreeOfCycles()
            .allowEmptyShould(true)

    // 다른 모듈의 영속 구현(*Entity, *Repository)은 참조하지 않는다. 공개 서비스·조회 인터페이스만 쓴다.
    @ArchTest
    val modulesUseOnlyPublicInterfaces: ArchRule =
        classes()
            .that(DescribedPredicate.describe("core 모듈 안의 클래스") { moduleOf(it.packageName) != null })
            .should(
                object : ArchCondition<JavaClass>("다른 모듈의 *Entity·*Repository를 참조하지 않는다") {
                    override fun check(
                        item: JavaClass,
                        events: ConditionEvents,
                    ) {
                        val from = moduleOf(item.packageName)
                        item.directDependenciesFromSelf.forEach { dep ->
                            val target = dep.targetClass
                            val to = moduleOf(target.packageName)
                            val name = target.simpleName
                            val persistence = name.endsWith("Entity") || name.endsWith("Repository")
                            if (to != null && to != from && persistence) {
                                events.add(SimpleConditionEvent.violated(dep, dep.description))
                            }
                        }
                    }
                },
            ).allowEmptyShould(true)

    private fun moduleOf(packageName: String): String? = MODULE.find(packageName)?.groupValues?.get(1)

    private companion object {
        val MODULE = Regex("""^com\.pipibooks\.core\.([^.]+)""")
    }
}
