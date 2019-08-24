package com.kalessil.phpStorm.phpInspectionsEA.inspectors.apiUsage.arrays;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.FeaturedPhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.settings.StrictnessCategory;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.OpenapiTypesUtil;
import org.jetbrains.annotations.NotNull;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class ExplodeLimitUsageInspector extends PhpInspection {

    @NotNull
    @Override
    public String getShortName() {
        return "ExplodeLimitUsageInspection";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "'explode(...)' limit can be used";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new FeaturedPhpElementVisitor() {
            @Override
            public void visitPhpFunctionCall(@NotNull FunctionReference reference) {
                if (this.shouldSkipAnalysis(reference, StrictnessCategory.STRICTNESS_CATEGORY_PERFORMANCE)) { return; }

                final String functionName = reference.getName();
                if (functionName != null && functionName.equals("explode")) {
                    final PsiElement[] arguments = reference.getParameters();
                    if (arguments.length == 2 && this.isTargetContext(reference)) {
                        holder.registerProblem(reference, "...");
                    }
                }
            }

            private boolean isTargetContext(@NotNull PsiElement expression) {
                final PsiElement parent = expression.getParent();
                if (parent instanceof ArrayAccessExpression) {
                    final ArrayIndex index = ((ArrayAccessExpression) parent).getIndex();
                    if (index != null) {
                        final PsiElement indexValue = index.getValue();
                        return indexValue != null && OpenapiTypesUtil.isNumber(index) && indexValue.getText().equals("0");
                    }
                } else if (OpenapiTypesUtil.isAssignment(parent)) {
                    final PsiElement container = ((AssignmentExpression) parent).getValue();
                    if (container instanceof Variable) {
                        final Function scope = ExpressionSemanticUtil.getScope(expression);
                        if (scope != null) {
                            final GroupStatement body = ExpressionSemanticUtil.getGroupStatement(scope);
                            if (body != null) {
                                final Variable variable      = (Variable) container;
                                final String variableName    = variable.getName();
                                boolean reachedStartingPoint = false;
                                for (final Variable match : PsiTreeUtil.findChildrenOfType(body, Variable.class)) {
                                    reachedStartingPoint = reachedStartingPoint || match == variable;
                                    if (reachedStartingPoint && match != variable && variableName.equals(match.getName())) {
                                        final PsiElement context = match.getParent();
                                        if (!(context instanceof ArrayAccessExpression) || !this.isTargetContext(match)) {
                                            return false;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // $parts = explode(..., $string); $parts[0] is only used, suggest adding limit
                // experiment with -N limit

                return false;
            }
        };
    }
}