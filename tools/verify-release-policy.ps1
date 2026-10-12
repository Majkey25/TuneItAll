[CmdletBinding()]
param(
    [string]$Repository = $env:GITHUB_REPOSITORY,
    [ValidateSet('play-signing', 'preview-signing', 'release-publication')][string]$ReleaseEnvironment,
    [ValidateSet('play', 'preview', 'release')][string]$ReleaseKind,
    [string]$ReleaseTag,
    [string]$RefType = $env:GITHUB_REF_TYPE,
    [string]$RefName = $env:GITHUB_REF_NAME,
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'

function Assert-MainProtection([pscustomobject]$Rule) {
    if ($null -eq $Rule -or $Rule.requiresStatusChecks -ne $true -or $Rule.requiresStrictStatusChecks -ne $true -or
        'Test, lint, and build' -cnotin @($Rule.requiredStatusCheckContexts) -or
        $Rule.isAdminEnforced -ne $true -or $Rule.allowsForcePushes -ne $false -or $Rule.allowsDeletions -ne $false -or
        $Rule.bypassForcePushAllowances.totalCount -ne 0) {
        throw 'Main protection must enforce strict Test, lint, and build checks and no admin/force-push/deletion bypass'
    }
}

function Assert-EnvironmentPolicy(
    [pscustomobject]$Policy, [pscustomobject]$Deployments,
    [string]$Name, [string]$Kind, [string]$Tag, [string]$CallerType, [string]$CallerName
) {
    if ($Policy.can_admins_bypass -ne $false -or
        $Policy.deployment_branch_policy.custom_branch_policies -ne $true -or
        $Policy.deployment_branch_policy.protected_branches -ne $false) {
        throw 'Release environment must enforce no bypass and explicit deployment refs'
    }
    if ($Tag -cnotmatch '^v[0-9]+\.[0-9]+\.[0-9]+(-(alpha|beta|rc)\.[0-9]+)?$' -or
        ($Kind -eq 'preview' -and $Tag -cnotmatch '-(alpha|beta|rc)\.[0-9]+$') -or
        ($Kind -eq 'release' -and $Tag -cmatch '-') -or
        -not (($CallerType -eq 'tag' -and $CallerName -ceq $Tag) -or
            ($Name -eq 'play-signing' -and $Kind -eq 'play' -and $CallerType -eq 'branch' -and $CallerName -ceq 'main'))) {
        throw 'Release caller must use its intended release tag, or main for a Play bundle'
    }
    $policies = @($Deployments.branch_policies)
    if ($policies.Count -eq 0 -or $Deployments.total_count -ne $policies.Count) {
        throw 'Release deployment policies are missing or incomplete'
    }
    $matchesCaller = $false
    foreach ($policy in $policies) {
        if (-not (($policy.type -eq 'tag' -and $policy.name -cin @('v*', 'v*-alpha.*', 'v*-beta.*', 'v*-rc.*')) -or
            ($Name -eq 'play-signing' -and $policy.type -eq 'branch' -and $policy.name -ceq 'main'))) {
            throw 'Release deployment policies permit an unintended ref'
        }
        if ($policy.type -eq $CallerType -and $CallerName -clike $policy.name) { $matchesCaller = $true }
    }
    if (-not $matchesCaller) { throw 'Release deployment policies do not permit the caller ref' }
}

if ($SelfTest) {
    $rule = [pscustomobject]@{
        requiresStatusChecks = $true; requiresStrictStatusChecks = $true; requiredStatusCheckContexts = @('Test, lint, and build')
        isAdminEnforced = $true; allowsForcePushes = $false; allowsDeletions = $false
        bypassForcePushAllowances = @{ totalCount = 0 }
    }
    $policy = [pscustomobject]@{
        can_admins_bypass = $false
        protection_rules = @()
        deployment_branch_policy = @{ custom_branch_policies = $true; protected_branches = $false }
    }
    $deployments = [pscustomobject]@{ total_count = 2; branch_policies = @(@{ type = 'branch'; name = 'main' }, @{ type = 'tag'; name = 'v*' }) }
    Assert-MainProtection $rule
    Assert-EnvironmentPolicy $policy $deployments 'play-signing' 'play' 'v0.3.0-alpha.37' 'branch' 'main'
    $tagPolicies = [pscustomobject]@{ total_count = 1; branch_policies = @(@{ type = 'tag'; name = 'v*' }) }
    Assert-EnvironmentPolicy $policy $tagPolicies 'preview-signing' 'preview' 'v0.3.0-alpha.37' 'tag' 'v0.3.0-alpha.37'
    Assert-EnvironmentPolicy $policy $tagPolicies 'release-publication' 'release' 'v0.3.0' 'tag' 'v0.3.0'
    $checks = 3
    foreach ($change in @(
        @{ requiresStatusChecks = $false }, @{ requiresStrictStatusChecks = $false }, @{ requiredStatusCheckContexts = @() },
        @{ requiredStatusCheckContexts = @('Unrelated check') },
        @{ isAdminEnforced = $false }, @{ allowsForcePushes = $true }, @{ allowsDeletions = $true },
        @{ bypassForcePushAllowances = @{ totalCount = 1 } }
    )) {
        $denied = $rule | ConvertTo-Json -Depth 6 | ConvertFrom-Json
        foreach ($property in $change.Keys) { $denied.$property = $change[$property] }
        try { Assert-MainProtection $denied }
        catch { if (-not $_.Exception.Message.StartsWith('Main protection')) { throw }; $checks++; continue }
        throw "Accepted unsafe main protection: $($change.Keys)"
    }
    foreach ($variant in @('admin-bypass', 'unrestricted-refs', 'wrong-ref', 'broad-ref', 'incomplete-refs', 'missing-refs')) {
        $denied = $policy | ConvertTo-Json -Depth 8 | ConvertFrom-Json
        $deniedDeployments = $deployments | ConvertTo-Json -Depth 6 | ConvertFrom-Json
        $caller = 'main'
        switch ($variant) {
            'admin-bypass' { $denied.can_admins_bypass = $true }
            'unrestricted-refs' { $denied.deployment_branch_policy = $null }
            'wrong-ref' { $caller = 'feature' }
            'broad-ref' { $deniedDeployments.branch_policies[1].name = '*' }
            'incomplete-refs' { $deniedDeployments.total_count = 3 }
            'missing-refs' { $deniedDeployments.total_count = 0; $deniedDeployments.branch_policies = @() }
        }
        try { Assert-EnvironmentPolicy $denied $deniedDeployments 'play-signing' 'play' 'v0.3.0-alpha.37' 'branch' $caller }
        catch { if (-not $_.Exception.Message.StartsWith('Release ')) { throw }; $checks++; continue }
        throw "Accepted unsafe release environment: $variant"
    }
    Write-Output "Passed $checks release policy checks"
    return
}

if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$' -or -not $ReleaseEnvironment -or -not $ReleaseKind) {
    throw 'Release repository, environment and kind are required'
}
$owner, $name = $Repository.Split('/')
$query = 'query($owner:String!,$name:String!){repository(owner:$owner,name:$name){ref(qualifiedName:"refs/heads/main"){branchProtectionRule{requiresStatusChecks requiresStrictStatusChecks requiredStatusCheckContexts isAdminEnforced allowsForcePushes allowsDeletions bypassForcePushAllowances{totalCount}}}}}'
$branchJson = & gh api graphql -f "query=$query" -f "owner=$owner" -f "name=$name"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect main protection' }
Assert-MainProtection (($branchJson | ConvertFrom-Json).data.repository.ref.branchProtectionRule)
$environmentJson = & gh api "repos/$Repository/environments/$ReleaseEnvironment"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect release environment' }
$deploymentJson = & gh api "repos/$Repository/environments/$ReleaseEnvironment/deployment-branch-policies?per_page=100"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect release deployment policies' }
Assert-EnvironmentPolicy ($environmentJson | ConvertFrom-Json) ($deploymentJson | ConvertFrom-Json) $ReleaseEnvironment $ReleaseKind $ReleaseTag $RefType $RefName
Write-Output 'Verified protected main and release deployment policies'
