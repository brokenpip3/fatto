package com.brokenpip3.fatto.data.filter

object TaskFilterExpressionParser {
    fun parse(expression: String): Result<TaskFilterExpression> =
        try {
            val tokens = tokenize(expression)
            if (tokens.isEmpty()) {
                Result.success(MatchAll)
            } else {
                Result.success(Parser(tokens).parseExpression())
            }
        } catch (e: TaskFilterParseException) {
            Result.failure(e)
        }

    private fun tokenize(expression: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false

        fun flushCurrent() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }

        for (char in expression) {
            when {
                char == '"' -> inQuote = !inQuote
                inQuote -> current.append(char)
                char.isWhitespace() -> flushCurrent()
                char == '(' || char == ')' -> {
                    flushCurrent()
                    tokens += char.toString()
                }
                else -> current.append(char)
            }
        }

        if (inQuote) throw TaskFilterParseException("Missing closing quote")
        flushCurrent()
        return tokens
    }

    private class Parser(
        private val tokens: List<String>,
    ) {
        private var index = 0

        fun parseExpression(): TaskFilterExpression {
            val expression = parseOr()
            if (!isAtEnd()) {
                val token = peek()
                if (token == ")") {
                    throw TaskFilterParseException("Unexpected closing parenthesis")
                }
                throw TaskFilterParseException("Unexpected token: $token")
            }
            return expression
        }

        private fun parseOr(): TaskFilterExpression {
            val children = mutableListOf(parseAnd())
            while (matchKeyword("or")) {
                children += parseAnd()
            }
            return collapse(children, ::OrExpression)
        }

        private fun parseAnd(): TaskFilterExpression {
            val children = mutableListOf(parseUnary())
            while (true) {
                when {
                    matchKeyword("and") -> {
                        ensureNotAtEnd()
                        children += parseUnary()
                    }
                    shouldParseImplicitAnd() -> children += parseUnary()
                    else -> return collapse(children, ::AndExpression)
                }
            }
        }

        private fun parseUnary(): TaskFilterExpression {
            if (isAtEnd()) unexpectedEndOfExpression()

            return when (peek()) {
                "(" -> parseGroupedExpression()
                ")" -> unexpectedClosingParenthesis()
                else -> parseTerm(advance())
            }
        }

        private fun parseGroupedExpression(): TaskFilterExpression {
            advance()
            val expression =
                if (check(")")) {
                    MatchAll
                } else {
                    parseOr()
                }
            if (!match(")")) missingClosingParenthesis()
            return expression
        }

        private fun parseTerm(token: String): TaskFilterExpression {
            if (token.equals("and", ignoreCase = true) || token.equals("or", ignoreCase = true)) {
                throw TaskFilterParseException("Unexpected operator: $token")
            }

            val negative = token.startsWith("-") && token.length > 1
            val signless = if (negative) token.substring(1) else token
            val explicitPositive = signless.startsWith("+") && signless.length > 1
            val body = if (explicitPositive) signless.substring(1) else signless

            val term =
                when {
                    body.startsWith("project:", ignoreCase = true) ->
                        parseProjectTerm(body)

                    body.contains(":") ->
                        throw TaskFilterParseException("Unsupported attribute: ${body.substringBefore(":")}")

                    explicitPositive && body in SupportedVirtualTags.names ->
                        VirtualTagTerm(body)

                    explicitPositive ->
                        TagTerm(body)

                    else ->
                        KeywordTerm(body)
                }

            return if (negative) NotExpression(term) else term
        }

        private fun parseProjectTerm(body: String): ProjectTerm {
            val project = body.substringAfter(":", "")
            if (project.isBlank()) {
                throw TaskFilterParseException("Empty project value")
            }
            return ProjectTerm(project)
        }

        private fun shouldParseImplicitAnd(): Boolean {
            if (isAtEnd()) return false
            val token = peek()
            return token != ")" && !token.equals("or", ignoreCase = true)
        }

        private fun collapse(
            children: List<TaskFilterExpression>,
            factory: (List<TaskFilterExpression>) -> TaskFilterExpression,
        ): TaskFilterExpression =
            when (children.size) {
                0 -> MatchAll
                1 -> children.first()
                else -> factory(children)
            }

        private fun match(token: String): Boolean {
            if (check(token)) {
                index++
                return true
            }
            return false
        }

        private fun matchKeyword(keyword: String): Boolean {
            if (!isAtEnd() && peek().equals(keyword, ignoreCase = true)) {
                index++
                return true
            }
            return false
        }

        private fun check(token: String): Boolean = !isAtEnd() && peek() == token

        private fun advance(): String {
            if (isAtEnd()) unexpectedEndOfExpression()
            return tokens[index++]
        }

        private fun ensureNotAtEnd() {
            if (isAtEnd()) unexpectedEndOfExpression()
        }

        private fun peek(): String = tokens[index]

        private fun isAtEnd(): Boolean = index >= tokens.size

        private fun unexpectedEndOfExpression(): Nothing {
            throw TaskFilterParseException("Unexpected end of expression")
        }

        private fun unexpectedClosingParenthesis(): Nothing {
            throw TaskFilterParseException("Unexpected closing parenthesis")
        }

        private fun missingClosingParenthesis(): Nothing {
            throw TaskFilterParseException("Missing closing parenthesis")
        }
    }
}
