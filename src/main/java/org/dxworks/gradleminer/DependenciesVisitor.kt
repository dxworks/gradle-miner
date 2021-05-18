package org.dxworks.gradleminer

import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.function.Consumer

class DependenciesVisitor(private val path: File) : CodeVisitorSupport() {
    private val dependencies: MutableList<GradleDependency> = ArrayList()
    private var inDependenciesBlock = false
    private var group: String? = null
    private var name: String? = null
    private var version: String? = null

    override fun visitMethodCallExpression(methodCallExpression: MethodCallExpression) {
        inDependenciesBlock = "dependencies" == methodCallExpression.methodAsString
        super.visitMethodCallExpression(methodCallExpression)
    }

    override fun visitAttributeExpression(attibute: AttributeExpression) {
        if(attibute is ConstantExpression)
            if (attibute.constantName == "group") {
                group = attibute.getText()
            } else if(attibute.constantName == "version"){
                version = attibute.getText()
            }

    }

    override fun visitArgumentlistExpression(argumentListExpression: ArgumentListExpression) {
        if (inDependenciesBlock) {
            val expressions = argumentListExpression.expressions
            if (expressions.size == 1 && expressions[0] is ClosureExpression) {
                val closureExpression = expressions[0] as ClosureExpression
                if (closureExpression.code is BlockStatement) {
                    val blockStatement = closureExpression.code as BlockStatement
                    blockStatement.statements.forEach(Consumer { statement: Statement ->
                        addDependencyFromStatement(
                            statement
                        )
                    })
                }
            }
        }
        super.visitArgumentlistExpression(argumentListExpression)
    }

    fun getDependencies(): List<GradleDependency> {
        return dependencies
    }

    private fun addDependencyFromStatement(statement: Statement) {
        var expression: Expression? = null
        try {
            expression = determineExpression(statement)
        } catch (e: NoSuchMethodException) {
            println(
                String.format(
                    "ExpressionStatement/ReturnStatement no longer have a 'getExpression' method: %s",
                    e.message
                )
            )
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            println(
                String.format(
                    "ExpressionStatement/ReturnStatement no longer have a 'getExpression' method: %s",
                    e.message
                )
            )
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            println(
                String.format(
                    "ExpressionStatement/ReturnStatement no longer have a 'getExpression' method: %s",
                    e.message
                )
            )
            e.printStackTrace()
        }
        if (expression is MethodCallExpression) {
            val argumentsExpression = expression.arguments
            if (argumentsExpression is ArgumentListExpression) {
                processArgumentListExpression(argumentsExpression)
            } else if (argumentsExpression is TupleExpression) {
                processTupleExpression(argumentsExpression)
            }
        }
    }

    private fun processArgumentListExpression(methodArgumentListExpression: ArgumentListExpression) {
        val methodExpressions = methodArgumentListExpression.expressions
        if (methodExpressions.size == 1 && methodExpressions[0] is ConstantExpression) {
            val methodConstantExpression = methodExpressions[0] as ConstantExpression
            addDependencyFromConstantExpression(methodConstantExpression)
        }
    }

    private fun processTupleExpression(tupleExpression: TupleExpression) {
        if (tupleExpression.expressions.size == 1 && tupleExpression.getExpression(0) is MapExpression) {
            addDependencyFromMapExpression(tupleExpression.getExpression(0) as MapExpression)
        }
    }

    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    private fun determineExpression(statement: Statement): Expression {
        val getExpression: Method
        getExpression = if (statement is ExpressionStatement) {
            ExpressionStatement::class.java.getMethod("getExpression")
        } else if (statement is ReturnStatement) {
            ReturnStatement::class.java.getMethod("getExpression")
        } else {
            throw NoSuchMethodException("Failed to find an expression.")
        }
        return getExpression.invoke(statement) as Expression
    }

    private fun addDependencyFromMapExpression(mapExpression: MapExpression) {
        val mapEntryExpressions = mapExpression.mapEntryExpressions
        var group: String? = null
        var name: String? = null
        var version: String? = null
        for (mapEntryExpression in mapEntryExpressions) {
            val key = mapEntryExpression.keyExpression.text
            val value = mapEntryExpression.valueExpression.text
            if ("group" == key) {
                group = value
            } else if ("name" == key) {
                name = value
            } else if ("version" == key) {
                version = value
            }
        }
        addDependency(group, name, version)
    }

    private fun addDependencyFromConstantExpression(constantExpression: ConstantExpression) {
        val dependencyString = constantExpression.text
        val pieces = dependencyString.split(":").toTypedArray()
        if (pieces.size == 3) {
            val group = pieces[0]
            val name = pieces[1]
            val version = pieces[2]
            addDependency(group, name, version)
        }
    }

    private fun addDependency(group: String?, name: String?, version: String?) {
        val dependency = GradleDependency(group, name, version)
        dependencies.add(dependency)
    }

    fun toGradleProject() = GradleProject(GradleProjectId(group, name, version), path, dependencies)

}
