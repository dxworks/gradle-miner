package org.dxworks.gradleminer

import groovy.lang.IntRange
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.expr.*
import java.util.*

class FindDependencyVisitor : CodeVisitorSupport() {
    private var dependencyLinesRange = IntRange(0, 0)
    private var dependencies: MutableList<GradleDependency> = ArrayList()
    private var group: String? = null
    private var name: String? = null
    private var version: String? = null

    override fun visitAttributeExpression(attibute: AttributeExpression) {
        if(attibute is ConstantExpression)
            if (attibute.constantName == "group") {
                group = attibute.getText()
            } else if(attibute.constantName == "version"){
                version = attibute.getText()
            }

    }

    override fun visitArgumentlistExpression(ale: ArgumentListExpression) {
        if (dependencyLinesRange.contains(ale.lineNumber)) {
            val expressions = ale.expressions
            for (e in expressions) {
                if (e is ConstantExpression) {
                    val depStr = e.getText()
                    val deps = depStr.split(":".toRegex()).toTypedArray()
                    if (deps.size == 3) {
                        dependencies.add(GradleDependency(deps[0], deps[1], deps[2]))
                    }
                }
                if (e is ListExpression) {
                    val listExpressions = e.expressions
                    for (le in listExpressions) {
                        if (le is ConstantExpression) {
                            val depStr = le.getText()
                            val deps = depStr.split(":".toRegex()).toTypedArray()
                            if (deps.size == 3) {
                                dependencies.add(GradleDependency(deps[0], deps[1], deps[2]))
                            }
                        }
                    }
                }
            }
        }
        super.visitArgumentlistExpression(ale)
    }

    override fun visitMethodCallExpression(call: MethodCallExpression) {
        if (call.methodAsString != "buildscript") {
            if (call.methodAsString == "dependencies") {
                if (dependencyLinesRange.equals(IntRange(0, 0))) {
                    dependencyLinesRange = IntRange(call.lineNumber, call.lastLineNumber)
                }
            }
            super.visitMethodCallExpression(call)
        }
    }

    override fun visitMapExpression(expression: MapExpression) {
        if (dependencyLinesRange.contains(expression.lineNumber)) {
            val mapEntryExpressions = expression.mapEntryExpressions
            val dependencyMap: MutableMap<String, String> = HashMap()
            for (mapEntryExpression in mapEntryExpressions) {
                val key = mapEntryExpression.keyExpression.text
                val value = mapEntryExpression.valueExpression.text
                dependencyMap[key] = value
            }
            if (dependencyMap.keys.containsAll(listOf("group", "name", "version"))) {
                val group = dependencyMap["group"]
                val name = dependencyMap["name"]
                val version = dependencyMap["version"]
                dependencies.add(GradleDependency(group, name!!, version))
            }
        }
        super.visitMapExpression(expression)
    }

    fun toGradleProject() = GradleProject(GradleProjectId(group, name, version), null, dependencies)
}
