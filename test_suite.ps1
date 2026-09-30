Write-Host "============================================================" -ForegroundColor Cyan
if ($failCount -gt 0) { exit 1 }
Write-Host "   RIMS FULL-STACK END-TO-END AUTOMATED TEST SUITE          " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$passCount = 0
$failCount = 0

function Assert-Test($condition, $testName) {
    if ($condition) {
        Write-Host "[PASS] $testName" -ForegroundColor Green
        $script:passCount++
    } else {
        Write-Host "[FAIL] $testName" -ForegroundColor Red
        $script:failCount++
    }
}

function Invoke-ApiSafe {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )
    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            Headers = $Headers
        }
        if ($Body) {
            $params["Body"] = $Body
            $params["ContentType"] = "application/json"
        }
        $resp = Invoke-RestMethod @params
        return @{ Success = $true; Data = $resp; StatusCode = 200 }
    } catch {
        $statusCode = 500
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        return @{ Success = $false; Error = $_.Exception.Message; StatusCode = $statusCode }
    }
}

# 1. Test Static Assets
Write-Host "`n--- 1. Testing Web Server & Static Assets ---" -ForegroundColor Yellow
$indexHtml = Invoke-WebRequest -Uri "http://localhost:8080/" -UseBasicParsing
Assert-Test ($indexHtml.StatusCode -eq 200 -and $indexHtml.Content -match "Rental Item Management System") "GET / serves index.html with RIMS branding"

$styleCss = Invoke-WebRequest -Uri "http://localhost:8080/style.css" -UseBasicParsing
Assert-Test ($styleCss.StatusCode -eq 200 -and $styleCss.Content -match "--color-primary-navy") "GET /style.css serves professional navy theme"

$appJs = Invoke-WebRequest -Uri "http://localhost:8080/app.js" -UseBasicParsing
Assert-Test ($appJs.StatusCode -eq 200 -and $appJs.Content -match "formatINR") "GET /app.js serves client controller with INR formatter"
$health = Invoke-ApiSafe -Uri "http://localhost:8080/api/health"
Assert-Test ($health.Success -and $health.Data.database -eq "connected") "GET /api/health confirms PostgreSQL connectivity"

# 2. Test Customer Authentication & Registration
Write-Host "`n--- 2. Testing Customer Registration & Auth ---" -ForegroundColor Yellow
$randEmail = "kavita_$(Get-Random)@example.com"
$regBody = @{
    name = "Kavita Rao"
    email = $randEmail
    phone = "+91 98765 99999"
    password = "SecurePassword123"
} | ConvertTo-Json

$regResp = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/register" -Method Post -Body $regBody
Assert-Test ($regResp.Success -and $regResp.Data.user.role -eq "CUSTOMER" -and $regResp.Data.token -ne $null) "Customer registration succeeds with role CUSTOMER"
$custToken = $regResp.Data.token

# Duplicate registration check
$dupResp = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/register" -Method Post -Body $regBody
Assert-Test ($dupResp.StatusCode -eq 409) "Duplicate email registration correctly returns 409 Conflict"

# Customer Login
$loginResp = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/login" -Method Post -Body (@{ email = $randEmail; password = "SecurePassword123" } | ConvertTo-Json)
Assert-Test ($loginResp.Success -and $loginResp.Data.user.email -eq $randEmail) "Customer login succeeds"
$custToken = $loginResp.Data.token

# Bad password
$badLogin = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/login" -Method Post -Body (@{ email = $randEmail; password = "WrongPassword" } | ConvertTo-Json)
Assert-Test ($badLogin.StatusCode -eq 401) "Bad password returns 401 Unauthorized"

# GET /api/auth/me
$custHeaders = @{ Authorization = "Bearer $custToken" }
$meResp = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/me" -Headers $custHeaders
Assert-Test ($meResp.Success -and $meResp.Data.user.name -eq "Kavita Rao") "GET /api/auth/me returns authenticated user details"

# 3. Test Admin Authentication & Access Control
Write-Host "`n--- 3. Testing Admin Authentication & Single-Admin Rule ---" -ForegroundColor Yellow
$adminEmail = if ($env:RIMS_SEED_ADMIN_EMAIL) { $env:RIMS_SEED_ADMIN_EMAIL } else { "admin@rental.com" }
$adminPassword = if ($env:RIMS_SEED_ADMIN_PASSWORD) { $env:RIMS_SEED_ADMIN_PASSWORD } else { "admin123" }
$adminLogin = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/login" -Method Post -Body (@{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json)
Assert-Test ($adminLogin.Success -and $adminLogin.Data.user.role -eq "ADMIN") "Admin login succeeds with role ADMIN"
$adminToken = $adminLogin.Data.token
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

# 4. Test Customer Profile
Write-Host "`n--- 4. Testing Profile Management ---" -ForegroundColor Yellow
$profile = Invoke-ApiSafe -Uri "http://localhost:8080/api/profile" -Headers $custHeaders
Assert-Test ($profile.Success -and $profile.Data.user.email -eq $randEmail -and $profile.Data.totalRentals -eq 0) "GET /api/profile returns accurate initial statistics"

$updateProfile = Invoke-ApiSafe -Uri "http://localhost:8080/api/profile" -Method Put -Headers $custHeaders -Body (@{ name = "Kavita Sharma"; phone = "+91 99999 88888" } | ConvertTo-Json)
Assert-Test ($updateProfile.Success -and $updateProfile.Data.user.name -eq "Kavita Sharma") "PUT /api/profile successfully updates name and phone"

# 5. Test Catalog, Search, and Category Filtering
Write-Host "`n--- 5. Testing Catalog, Search & Filtering ---" -ForegroundColor Yellow
$allItems = Invoke-ApiSafe -Uri "http://localhost:8080/api/items"
Assert-Test ($allItems.Success -and $allItems.Data.Count -ge 8) "GET /api/items returns catalog items"

$photoItems = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?category=Photography"
$allPhoto = ($photoItems.Data | Where-Object { $_.category -ne "Photography" }).Count -eq 0
Assert-Test ($photoItems.Success -and $allPhoto -and $photoItems.Data.Count -ge 2) "GET /api/items?category=Photography filters accurately"

$searchItems = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?search=DJI"
Assert-Test ($searchItems.Success -and $searchItems.Data.Count -eq 1 -and $searchItems.Data[0].itemId -eq "ITM-102") "GET /api/items?search=DJI accurately finds Drone"

# 6. Test Rental Flow & Calculations (Customer)
Write-Host "`n--- 6. Testing Rental Flow & Price Calculations ---" -ForegroundColor Yellow
$bookStart = (Get-Date).Date.AddDays(10).ToString("yyyy-MM-dd")
$bookEnd = (Get-Date).Date.AddDays(13).ToString("yyyy-MM-dd")
$bookBody = @{
    itemId = "ITM-103"
    startDate = $bookStart
    endDate = $bookEnd
    paymentMethod = "UPI"
} | ConvertTo-Json

# ITM-103 price is ₹3,500/day. 10th to 13th is 3 days = ₹10,500
$rentalRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $bookBody
$r = $rentalRes.Data.rental
Assert-Test ($rentalRes.Success -and $r.numberOfDays -eq 3 -and $r.totalAmount -eq 10500.0) "Rental created: 3 days x ₹3,500 = ₹10,500 exactly"
Assert-Test ($rentalRes.Success -and $rentalRes.Data.payment.status -eq "PAID" -and $rentalRes.Data.payment.method -eq "UPI" -and $rentalRes.Data.payment.demoTransactionRef -like "DEMO-*") "Simulated UPI payment is separately recorded as PAID with a demo reference"
$rentalId = $r.rentalId

# Check availability for the selected range, then for a later non-overlapping range.
$item103 = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?id=ITM-103&startDate=$bookStart&endDate=$bookEnd"
Assert-Test ($item103.Success -and $item103.Data.available -eq $false) "Rented item ITM-103 is unavailable for overlapping dates"
$laterStart = $bookEnd
$laterEnd = (Get-Date).Date.AddDays(15).ToString("yyyy-MM-dd")
$laterAvailability = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?id=ITM-103&startDate=$laterStart&endDate=$laterEnd"
Assert-Test ($laterAvailability.Success -and $laterAvailability.Data.available -eq $true) "ITM-103 remains bookable for a later non-overlapping date range"

# Try to double-book ITM-103
$dupRent = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $bookBody
Assert-Test ($dupRent.StatusCode -eq 400) "Booking unavailable item correctly returns 400 Bad Request"

# Verify Customer's My Rentals
$myRentals = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/my" -Headers $custHeaders
Assert-Test ($myRentals.Success -and $myRentals.Data.Count -eq 1 -and $myRentals.Data[0].rentalId -eq $rentalId -and $myRentals.Data[0].status -eq "CONFIRMED" -and $myRentals.Data[0].paymentStatus -eq "PAID") "GET /api/rentals/my shows booking and separate payment status"

# Customer returns the rental
$returnResp = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/status" -Method Put -Headers $custHeaders -Body (@{ rentalId = $rentalId; status = "RETURNED" } | ConvertTo-Json)
Assert-Test ($returnResp.Success -and $returnResp.Data.rental.status -eq "RETURNED") "Customer successfully returns item via status API"

# Verify item availability is restored
$item103Restored = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?id=ITM-103"
Assert-Test ($item103Restored.Success -and $item103Restored.Data.available -eq $true) "Item ITM-103 availability automatically restored to TRUE after return"

# Test Cash on Pickup payment method (Payment Pending)
$cashBookBody = @{
    itemId = "ITM-103"
    startDate = (Get-Date).Date.AddDays(16).ToString("yyyy-MM-dd")
    endDate = (Get-Date).Date.AddDays(18).ToString("yyyy-MM-dd")
    paymentMethod = "CASH"
} | ConvertTo-Json
$cashRentalRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $cashBookBody
$rc = $cashRentalRes.Data.rental
Assert-Test ($cashRentalRes.Success -and $rc.status -eq "CONFIRMED" -and $rc.paymentStatus -eq "PENDING" -and $rc.paymentMethod -eq "CASH_ON_PICKUP" -and $cashRentalRes.Data.payment.status -eq "PENDING") "Cash-on-pickup confirms the booking while payment remains PENDING"

# Clean up cash rental
$returnCash = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/status" -Method Put -Headers $custHeaders -Body (@{ rentalId = $rc.rentalId; status = "RETURNED" } | ConvertTo-Json)
Assert-Test ($returnCash.Success) "Cash rental returned successfully"

# Test Card payment method (PAID)
$cardBookBody = @{
    itemId = "ITM-103"
    startDate = (Get-Date).Date.AddDays(21).ToString("yyyy-MM-dd")
    endDate = (Get-Date).Date.AddDays(23).ToString("yyyy-MM-dd")
    paymentMethod = "CREDIT_CARD"
} | ConvertTo-Json
$cardRentalRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $cardBookBody
$rcard = $cardRentalRes.Data.rental
Assert-Test ($cardRentalRes.Success -and $rcard.status -eq "CONFIRMED" -and $rcard.paymentStatus -eq "PAID" -and $cardRentalRes.Data.payment.demoTransactionRef -like "DEMO-*") "Credit Card test flow confirms booking and records a PAID demo payment"

# Clean up card rental
$returnCard = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/status" -Method Put -Headers $custHeaders -Body (@{ rentalId = $rcard.rentalId; status = "RETURNED" } | ConvertTo-Json)
Assert-Test ($returnCard.Success) "Card rental returned successfully"

# Failed simulated Net Banking payment keeps booking and payment states distinct.
$failedBody = @{
    itemId = "ITM-104"
    startDate = (Get-Date).Date.AddDays(30).ToString("yyyy-MM-dd")
    endDate = (Get-Date).Date.AddDays(32).ToString("yyyy-MM-dd")
    paymentMethod = "NET_BANKING"
    paymentOutcome = "FAILED"
} | ConvertTo-Json
$failedRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $failedBody
Assert-Test ($failedRes.Success -and $failedRes.Data.rental.status -eq "CANCELLED" -and $failedRes.Data.rental.paymentStatus -eq "FAILED" -and $failedRes.Data.payment.status -eq "FAILED") "Failed simulated payment is stored independently and leaves no confirmed booking"

# Test the complete overdue return and late-fee payment path using a backdated test fixture.
$lateFutureStart = (Get-Date).Date.AddDays(40).ToString("yyyy-MM-dd")
$lateFutureEnd = (Get-Date).Date.AddDays(42).ToString("yyyy-MM-dd")
$lateBookingRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body (@{
    itemId = "ITM-105"
    startDate = $lateFutureStart
    endDate = $lateFutureEnd
    paymentMethod = "UPI"
} | ConvertTo-Json)
$lateBooking = $lateBookingRes.Data.rental
Assert-Test ($lateBookingRes.Success -and $lateBooking.paymentStatus -eq "PAID") "Late-fee scenario booking and simulated rental payment are persisted"

$testSupport = Join-Path $PSScriptRoot "test_support"
$testSupportSource = Join-Path $testSupport "DatabaseTestSupport.java"
$postgresDriver = Get-ChildItem (Join-Path $PSScriptRoot "target\dependency") -Filter "postgresql-*.jar" | Select-Object -First 1
if (-not $postgresDriver) { throw "PostgreSQL JDBC driver missing. Run Maven dependency:copy-dependencies before the suite." }
$jdbcClasspath = "$PSScriptRoot\target\classes;$($postgresDriver.FullName)"
& javac -cp $jdbcClasspath -d $testSupport $testSupportSource
if ($LASTEXITCODE -ne 0) { throw "Could not compile the late-fee test fixture helper" }
$pastStart = (Get-Date).Date.AddDays(-3).ToString("yyyy-MM-dd")
$pastEnd = (Get-Date).Date.AddDays(-1).ToString("yyyy-MM-dd")
$helperClasspath = "$testSupport;$jdbcClasspath"
& java -cp $helperClasspath DatabaseTestSupport $lateBooking.rentalId $pastStart $pastEnd
if ($LASTEXITCODE -ne 0) { throw "Could not prepare the late-fee test booking" }

$actualReturnDate = (Get-Date).Date.ToString("yyyy-MM-dd")
$lateReturnRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/status" -Method Put -Headers $adminHeaders -Body (@{
    rentalId = $lateBooking.rentalId
    status = "RETURNED"
    actualReturnDate = $actualReturnDate
} | ConvertTo-Json)
$expectedLateFee = [math]::Round(1.5 * $lateBooking.dailyRate, 2)
$expectedTotalDue = $lateBooking.totalAmount + $expectedLateFee
Assert-Test ($lateReturnRes.Success -and $lateReturnRes.Data.rental.status -eq "RETURNED" -and $lateReturnRes.Data.rental.lateDays -eq 1 -and $lateReturnRes.Data.rental.lateFee -eq $expectedLateFee -and $lateReturnRes.Data.rental.totalDue -eq $expectedTotalDue -and $lateReturnRes.Data.rental.lateFeeStatus -eq "PENDING") "Late return stores one late day, 1.5x fee, and total amount due"

$lateFeePay = Invoke-ApiSafe -Uri "http://localhost:8080/api/payments/simulate" -Method Post -Headers $custHeaders -Body (@{
    rentalId = $lateBooking.rentalId
    paymentMethod = "NET_BANKING"
} | ConvertTo-Json)
Assert-Test ($lateFeePay.Success -and $lateFeePay.Data.payment.status -eq "PAID" -and $lateFeePay.Data.payment.method -eq "NET_BANKING" -and $lateFeePay.Data.payment.demoTransactionRef -like "DEMO-*" -and $lateFeePay.Data.rental.lateFeeStatus -eq "PAID") "Late fee settles as a separate simulated payment with a demo reference"

# 7. Test Admin Inventory Management (CRUD)
Write-Host "`n--- 7. Testing Admin Inventory CRUD ---" -ForegroundColor Yellow
$newItem = @{
    name = "Canon EOS R5 Mirrorless Camera"
    category = "Photography"
    description = "45MP full-frame 8K video mirrorless camera body"
    rentalPrice = "5500.0"
    imageUrl = "camera"
} | ConvertTo-Json

$addRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/items" -Method Post -Headers $adminHeaders -Body $newItem
Assert-Test ($addRes.Success -and $addRes.Data.item.name -eq "Canon EOS R5 Mirrorless Camera") "Admin adds new item with ₹5,500/day rate"
$newItemId = $addRes.Data.item.itemId

# Admin updates item price
$editBody = @{
    itemId = $newItemId
    name = "Canon EOS R5 Mirrorless Camera (Mark I)"
    rentalPrice = "6000.0"
    category = "Photography"
} | ConvertTo-Json
$editRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/items" -Method Put -Headers $adminHeaders -Body $editBody
Assert-Test ($editRes.Success -and $editRes.Data.item.rentalPrice -eq 6000.0) "Admin modifies item price to ₹6,000/day and saves to DB"

# Customer can immediately see updated price in catalog
$catalogCheck = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?id=$newItemId"
Assert-Test ($catalogCheck.Success -and $catalogCheck.Data.rentalPrice -eq 6000.0) "Customer catalog reflects updated ₹6,000/day price immediately"

# Admin deletes the item
$delRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?id=$newItemId" -Method Delete -Headers $adminHeaders
Assert-Test ($delRes.Success -and $delRes.Data.success -eq $true) "Admin successfully deletes item from inventory"

# 8. Test Role-Based Security & Permissions
Write-Host "`n--- 8. Testing Role-Based Authorization Guard ---" -ForegroundColor Yellow
$hackItem = Invoke-ApiSafe -Uri "http://localhost:8080/api/items" -Method Post -Headers $custHeaders -Body $newItem
Assert-Test ($hackItem.StatusCode -eq 403) "Customer blocked from POST /api/items with 403 Forbidden"

$hackCust = Invoke-ApiSafe -Uri "http://localhost:8080/api/customers" -Headers $custHeaders
Assert-Test ($hackCust.StatusCode -eq 403) "Customer blocked from GET /api/customers with 403 Forbidden"

$hackAnalytics = Invoke-ApiSafe -Uri "http://localhost:8080/api/admin/analytics" -Headers $custHeaders
Assert-Test ($hackAnalytics.StatusCode -eq 403) "Customer blocked from GET /api/admin/analytics with 403 Forbidden"

# 9. Test Admin Customers, Rentals & Analytics
Write-Host "`n--- 9. Testing Admin Dashboard, Customers & Analytics ---" -ForegroundColor Yellow
$customersList = Invoke-ApiSafe -Uri "http://localhost:8080/api/customers" -Headers $adminHeaders
Assert-Test ($customersList.Success -and $customersList.Data.Count -ge 2) "Admin accesses customer directory (found $($customersList.Data.Count) customers)"

$allRentals = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Headers $adminHeaders
Assert-Test ($allRentals.Success -and $allRentals.Data.Count -ge 1) "Admin views all rentals across customers"

$analytics = Invoke-ApiSafe -Uri "http://localhost:8080/api/admin/analytics" -Headers $adminHeaders
Assert-Test ($analytics.Success -and $analytics.Data.totalRevenue -gt 0 -and $analytics.Data.totalItems -ge 8 -and $analytics.Data.categoryStats -ne $null) "Admin analytics aggregates real revenue (₹$($analytics.Data.totalRevenue)) and category stats"

# 10. Test Logout & Session Invalidation
Write-Host "`n--- 10. Testing Logout & Session Invalidation ---" -ForegroundColor Yellow
$logoutResp = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/logout" -Method Post -Headers $custHeaders
Assert-Test ($logoutResp.Success -and $logoutResp.Data.success -eq $true) "POST /api/auth/logout succeeds"

$invalidCheck = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/me" -Headers $custHeaders
Assert-Test ($invalidCheck.StatusCode -eq 401) "Invalidated token returns 401 Unauthorized"

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "   TEST RESULTS: $passCount PASSED, $failCount FAILED        " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
