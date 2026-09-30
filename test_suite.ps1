Write-Host "============================================================" -ForegroundColor Cyan
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
$adminLogin = Invoke-ApiSafe -Uri "http://localhost:8080/api/auth/login" -Method Post -Body (@{ email = "admin@rental.com"; password = "admin123" } | ConvertTo-Json)
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
$bookBody = @{
    itemId = "ITM-103"
    startDate = "2026-10-10"
    endDate = "2026-10-13"
    paymentMethod = "UPI"
} | ConvertTo-Json

# ITM-103 price is ₹3,500/day. 10th to 13th is 3 days = ₹10,500
$rentalRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $bookBody
$r = $rentalRes.Data.rental
Assert-Test ($rentalRes.Success -and $r.numberOfDays -eq 3 -and $r.totalAmount -eq 10500.0) "Rental created: 3 days x ₹3,500 = ₹10,500 exactly"
$rentalId = $r.rentalId

# Verify item is now unavailable
$item103 = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?id=ITM-103"
Assert-Test ($item103.Success -and $item103.Data.available -eq $false) "Rented item ITM-103 automatically marked unavailable in database"

# Try to double-book ITM-103
$dupRent = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $bookBody
Assert-Test ($dupRent.StatusCode -eq 400) "Booking unavailable item correctly returns 400 Bad Request"

# Verify Customer's My Rentals
$myRentals = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/my" -Headers $custHeaders
Assert-Test ($myRentals.Success -and $myRentals.Data.Count -eq 1 -and $myRentals.Data[0].rentalId -eq $rentalId -and $myRentals.Data[0].status -eq "ACTIVE") "GET /api/rentals/my displays customer's active rental"

# Customer returns the rental
$returnResp = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/status" -Method Put -Headers $custHeaders -Body (@{ rentalId = $rentalId; status = "RETURNED" } | ConvertTo-Json)
Assert-Test ($returnResp.Success -and $returnResp.Data.rental.status -eq "RETURNED") "Customer successfully returns item via status API"

# Verify item availability is restored
$item103Restored = Invoke-ApiSafe -Uri "http://localhost:8080/api/items?id=ITM-103"
Assert-Test ($item103Restored.Success -and $item103Restored.Data.available -eq $true) "Item ITM-103 availability automatically restored to TRUE after return"

# Test Cash on Pickup payment method (Payment Pending)
$cashBookBody = @{
    itemId = "ITM-103"
    startDate = "2026-10-15"
    endDate = "2026-10-17"
    paymentMethod = "CASH"
} | ConvertTo-Json
$cashRentalRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $cashBookBody
$rc = $cashRentalRes.Data.rental
Assert-Test ($cashRentalRes.Success -and $rc.status -eq "PENDING" -and $rc.paymentStatus -eq "PENDING") "Cash on Pickup creates rental with status PENDING and paymentStatus PENDING"

# Clean up cash rental
$returnCash = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/status" -Method Put -Headers $custHeaders -Body (@{ rentalId = $rc.rentalId; status = "RETURNED" } | ConvertTo-Json)
Assert-Test ($returnCash.Success) "Cash rental returned successfully"

# Test Card payment method (PAID)
$cardBookBody = @{
    itemId = "ITM-103"
    startDate = "2026-10-20"
    endDate = "2026-10-22"
    paymentMethod = "CREDIT_CARD"
} | ConvertTo-Json
$cardRentalRes = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals" -Method Post -Headers $custHeaders -Body $cardBookBody
$rcard = $cardRentalRes.Data.rental
Assert-Test ($cardRentalRes.Success -and $rcard.status -eq "ACTIVE" -and $rcard.paymentStatus -eq "PAID") "Credit Card creates rental with status ACTIVE and paymentStatus PAID"

# Clean up card rental
$returnCard = Invoke-ApiSafe -Uri "http://localhost:8080/api/rentals/status" -Method Put -Headers $custHeaders -Body (@{ rentalId = $rcard.rentalId; status = "RETURNED" } | ConvertTo-Json)
Assert-Test ($returnCard.Success) "Card rental returned successfully"

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
