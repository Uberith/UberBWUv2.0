$ErrorActionPreference = 'Stop'

function Get-NumericIdsFromJson($obj) {
  $ids = @()
  if ($null -eq $obj) { return $ids }
  if ($obj -is [System.Collections.IEnumerable] -and -not ($obj -is [string])) {
    foreach ($e in $obj) { $ids += Get-NumericIdsFromJson $e }
    return $ids
  }
  if ($obj -is [pscustomobject]) {
    foreach ($prop in $obj.PSObject.Properties) {
      $name = $prop.Name
      $val = $prop.Value
      if ($name -match '^[0-9]+$') { $ids += [int]$name }
      if ($name -match '^[0-9]+\-[0-9]+$') {
        $parts = $name.Split('-'); $start=[int]$parts[0]; $end=[int]$parts[1]
        $ids += $start..$end
      }
      $ids += Get-NumericIdsFromJson $val
    }
  }
  return $ids
}

$files = Get-ChildItem -Path 'datasets/rs3/knowledge' -Filter *.json -Recurse
$all = @{}
foreach ($f in $files) {
  try {
    $json = Get-Content $f.FullName -Raw | ConvertFrom-Json -Depth 50
  } catch { Write-Host "Failed to parse $($f.FullName)"; continue }
  $ids = Get-NumericIdsFromJson $json | Where-Object { $_ -is [int] }
  $all[$f.FullName] = $ids
}

$dupMap = @{}
foreach ($kv in $all.GetEnumerator()) {
  foreach ($id in $kv.Value) {
    if (-not $dupMap.ContainsKey($id)) { $dupMap[$id] = @() }
    $dupMap[$id] += $kv.Key
  }
}

$dups = $dupMap.GetEnumerator() | Where-Object { $_.Value.Count -gt 1 } | Sort-Object Name
Write-Host "Datasets scanned: $($files.Count)"
Write-Host "Total unique ids: $($dupMap.Keys.Count)"
Write-Host "Overlapping ids across files: $($dups.Count)"
if ($dups.Count -gt 0) {
  foreach ($d in $dups) {
    Write-Host ("ID {0} appears in:" -f $d.Key)
    $d.Value | Sort-Object -Unique | ForEach-Object { Write-Host "  - $_" }
  }
}

