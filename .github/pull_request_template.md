## Description
<!-- Provide a clear and concise description of your changes -->

## Type of Change
<!-- Mark the relevant option(s) with an 'x' -->
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Code refactoring (no functional changes)
- [ ] Performance improvement
- [ ] Test coverage improvement
- [ ] Build/CI configuration

## Related Issues
<!-- Link related issues using keywords: Fixes #123, Closes #456, Relates to #789 -->
Fixes #

## Changes Made
<!-- List the key changes in bullet points -->
- 
-
-

## Testing
<!-- Describe the tests you ran and how to reproduce them -->

### Test Environment
- **OS:**
- **Java Version:**
- **Scala Version:**

### Test Cases
<!-- Check all that apply -->
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed
- [ ] All existing tests pass (`sbt test`)
- [ ] Tested with sample PDF files
- [ ] Tested with image files (PNG/JPG/etc)
- [ ] Tested batch mode
- [ ] Tested parallel processing

### Test Commands
<!-- Paste commands used to test your changes -->
```bash
# Example:
sbt test
sbt "testOnly com.visualdiff.core.DiffEngineSpec"
```

## Screenshots/Output
<!-- If applicable, add screenshots or sample output showing your changes -->
<!-- Before/After comparisons are especially helpful -->

## Code Quality Checklist
<!-- Ensure your code meets quality standards -->
- [ ] Code follows the project's style guidelines
- [ ] Code has been formatted with `sbt scalafmtAll`
- [ ] No linting errors (`sbt scalafixAll --check`)
- [ ] Code is properly documented (Scaladoc comments for public APIs)
- [ ] No compiler warnings
- [ ] Changes are backward compatible (or breaking changes are documented)

## Documentation
<!-- Update relevant documentation -->
- [ ] README.md updated (if needed)
- [ ] Code comments added/updated
- [ ] CLI help text updated (if adding/changing options)
- [ ] Example files added/updated

## Performance Impact
<!-- If applicable, describe any performance implications -->
- [ ] No performance impact
- [ ] Performance improved
- [ ] Performance impact acceptable (explain below)

<!-- If performance changed, provide details: -->

## Additional Notes
<!-- Any additional information reviewers should know -->

## Reviewer Checklist
<!-- For maintainers reviewing this PR -->
- [ ] Code review completed
- [ ] Tests are adequate
- [ ] Documentation is sufficient
- [ ] No security concerns
- [ ] Ready to merge
