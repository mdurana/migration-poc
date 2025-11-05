USE mysql_db;

-- 1. Main user account
CREATE TABLE tbl_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_uuid VARCHAR(36) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    pin_hash VARCHAR(255) NOT NULL,
    user_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_VERIFICATION', -- e.g., PENDING, ACTIVE, SUSPENDED, BANNED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone_number (phone_number),
    INDEX idx_user_uuid (user_uuid)
);

-- 2. Detailed user profile information
CREATE TABLE tbl_user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    middle_name VARCHAR(100),
    date_of_birth DATE,
    nationality VARCHAR(100),
    gender VARCHAR(20),
    profile_picture_url VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 3. User addresses
CREATE TABLE tbl_user_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    address_type VARCHAR(50) NOT NULL, -- e.g., HOME, WORK
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state_province VARCHAR(100),
    postal_code VARCHAR(20),
    country_code VARCHAR(3) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 4. User contact methods
CREATE TABLE tbl_user_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    contact_type VARCHAR(50) NOT NULL, -- e.g., SECONDARY_PHONE, WORK_EMAIL
    contact_value VARCHAR(255) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 5. KYC (Know Your Customer) documents
CREATE TABLE tbl_user_kyc_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL, -- e.g., PASSPORT, DRIVERS_LICENSE
    document_number VARCHAR(100),
    document_url VARCHAR(512) NOT NULL,
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, VERIFIED, REJECTED
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 6. Log of KYC status changes
CREATE TABLE tbl_kyc_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    admin_user_id BIGINT, -- The admin who approved/rejected
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 7. User-registered devices
CREATE TABLE tbl_user_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id_hash VARCHAR(255) NOT NULL,
    device_model VARCHAR(100),
    os_type VARCHAR(50), -- e.g., IOS, ANDROID
    os_version VARCHAR(50),
    app_version VARCHAR(50),
    fcm_token TEXT, -- Firebase Cloud Messaging token
    last_login TIMESTAMP,
    is_trusted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_device_id_hash (device_id_hash)
);

-- 8. User settings and preferences
CREATE TABLE tbl_user_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    prefers_email_notifications BOOLEAN DEFAULT TRUE,
    prefers_sms_notifications BOOLEAN DEFAULT TRUE,
    prefers_push_notifications BOOLEAN DEFAULT TRUE,
    language_code VARCHAR(10) DEFAULT 'en',
    theme VARCHAR(20) DEFAULT 'LIGHT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 9. User segments for marketing
CREATE TABLE tbl_user_segments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    segment_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10. Mapping table for users to segments
CREATE TABLE tbl_user_segment_map (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    segment_id BIGINT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_segment_id (segment_id)
);

-- 11. User referral codes
CREATE TABLE tbl_referral_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    referral_code VARCHAR(20) NOT NULL UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 12. Log of successful referrals
CREATE TABLE tbl_user_referrals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    referrer_user_id BIGINT NOT NULL,
    referred_user_id BIGINT NOT NULL,
    referral_code_used VARCHAR(20),
    status VARCHAR(50) DEFAULT 'PENDING', -- e.g., PENDING, COMPLETED, REWARDED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_referrer_user_id (referrer_user_id),
    INDEX idx_referred_user_id (referred_user_id)
);

-- 13. Profile pictures history
CREATE TABLE tbl_user_profile_pictures (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    is_active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 14. AML/Identity verification check logs
CREATE TABLE tbl_user_identity_checks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    check_provider VARCHAR(100), -- e.g., ComplyAdvantage, Onfido
    check_type VARCHAR(50), -- e.g., AML_SCREENING, ID_VERIFICATION
    check_reference_id VARCHAR(255),
    status VARCHAR(50), -- e.g., SUBMITTED, PASS, FAIL, REQUIRES_REVIEW
    payload_sent TEXT,
    response_received TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 15. Wallet types
CREATE TABLE tbl_wallet_types (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type_code VARCHAR(50) NOT NULL UNIQUE, -- e.g., PERSONAL, BUSINESS, SAVINGS
    description VARCHAR(255),
    is_default BOOLEAN DEFAULT FALSE
);

-- 16. The main wallet for a user
CREATE TABLE tbl_wallets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    wallet_type_id INT NOT NULL,
    wallet_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- e.g., ACTIVE, FROZEN, CLOSED
    primary_currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_wallet_uuid (wallet_uuid)
);

-- 17. Balances for each currency within a wallet
CREATE TABLE tbl_wallet_balances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    available_balance DECIMAL(18, 4) NOT NULL DEFAULT 0.00,
    on_hold_balance DECIMAL(18, 4) NOT NULL DEFAULT 0.00, -- For pending transactions
    total_balance DECIMAL(18, 4) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_currency (wallet_id, currency_code)
);

-- 18. Transaction and velocity limits for a wallet
CREATE TABLE tbl_wallet_limits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    limit_type VARCHAR(50) NOT NULL, -- e.g., DAILY_TRANSACTION, MONTHLY_IN, DAILY_OUT
    limit_value DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wallet_id (wallet_id)
);

-- 19. Tracks current usage against limits
CREATE TABLE tbl_wallet_limit_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    limit_type VARCHAR(50) NOT NULL,
    usage_period VARCHAR(20) NOT NULL, -- e.g., 2025-10-23
    current_usage DECIMAL(18, 4) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_limit_period (wallet_id, limit_type, usage_period)
);

-- 20. History of wallet status changes
CREATE TABLE tbl_wallet_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    reason TEXT,
    changed_by_user_id BIGINT, -- Admin or system
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wallet_id (wallet_id)
);

-- 21. Supported currencies
CREATE TABLE tbl_currencies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    currency_code VARCHAR(3) NOT NULL UNIQUE,
    currency_name VARCHAR(100),
    symbol VARCHAR(5),
    decimal_places INT NOT NULL DEFAULT 2,
    is_active BOOLEAN DEFAULT TRUE
);

-- 22. Exchange rates for currency conversion
CREATE TABLE tbl_exchange_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_currency_code VARCHAR(3) NOT NULL,
    target_currency_code VARCHAR(3) NOT NULL,
    rate DECIMAL(18, 8) NOT NULL,
    provider VARCHAR(100),
    valid_from TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    valid_to TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_base_target (base_currency_code, target_currency_code)
);

-- 23. Special savings "pots" or "goals"
CREATE TABLE tbl_savings_pots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    pot_name VARCHAR(100) NOT NULL,
    target_amount DECIMAL(18, 4),
    current_balance DECIMAL(18, 4) NOT NULL DEFAULT 0.00,
    currency_code VARCHAR(3) NOT NULL,
    is_locked BOOLEAN DEFAULT FALSE,
    unlock_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wallet_id (wallet_id)
);

-- 24. Monthly/Periodic wallet statements
CREATE TABLE tbl_wallet_statements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    statement_period VARCHAR(20), -- e.g., 2025-09
    statement_url VARCHAR(512) NOT NULL,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    opening_balance DECIMAL(18, 4),
    closing_balance DECIMAL(18, 4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wallet_id (wallet_id)
);

-- 25. Master transaction log
CREATE TABLE tbl_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_uuid VARCHAR(36) NOT NULL UNIQUE,
    source_wallet_id BIGINT, -- Can be null for cash-in
    destination_wallet_id BIGINT, -- Can be null for cash-out/payments
    transaction_type_id INT NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    fee_amount DECIMAL(18, 4) DEFAULT 0.00,
    total_amount DECIMAL(18, 4) NOT NULL, -- amount + fee
    status VARCHAR(50) NOT NULL, -- e.g., PENDING, COMPLETED, FAILED, REVERSED
    external_reference_id VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_source_wallet_id (source_wallet_id),
    INDEX idx_destination_wallet_id (destination_wallet_id),
    INDEX idx_transaction_uuid (transaction_uuid)
);

-- 26. The double-entry accounting ledger
CREATE TABLE tbl_transaction_ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    entry_type VARCHAR(10) NOT NULL, -- DEBIT or CREDIT
    amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    balance_before DECIMAL(18, 4),
    balance_after DECIMAL(18, 4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_wallet_id (wallet_id)
);

-- 27. Holds for pending transactions
CREATE TABLE tbl_transaction_holds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_id BIGINT NOT NULL,
    transaction_id BIGINT,
    hold_reason VARCHAR(255),
    amount_on_hold DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, RELEASED, CAPTURED
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wallet_id (wallet_id)
);

-- 28. Scheduled or recurring transfers
CREATE TABLE tbl_scheduled_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_wallet_id BIGINT NOT NULL,
    destination_wallet_id BIGINT NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    frequency VARCHAR(50), -- e.g., DAILY, WEEKLY, MONTHLY
    next_run_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAUSED, COMPLETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_source_wallet_id (source_wallet_id)
);

-- 29. Log of scheduled transfer runs
CREATE TABLE tbl_scheduled_transfer_runs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scheduled_transfer_id BIGINT NOT NULL,
    run_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL, -- e.g., SUCCESS, FAILED
    resulting_transaction_id BIGINT,
    error_message TEXT,
    INDEX idx_scheduled_transfer_id (scheduled_transfer_id)
);

-- 30. Defines the types of transactions
CREATE TABLE tbl_transaction_types (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type_code VARCHAR(50) NOT NULL UNIQUE, -- e.g., P2P_TRANSFER, BILL_PAYMENT, CASH_IN
    description VARCHAR(255),
    is_credit BOOLEAN, -- Is this typically a credit or debit?
    is_debit BOOLEAN
);

-- 31. Fee calculation models
CREATE TABLE tbl_fee_models (
    id INT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(100) NOT NULL,
    fee_type VARCHAR(20) NOT NULL, -- e.g., FIXED, PERCENTAGE, TIERED
    fee_config JSON, -- Stores tiered rates or percentage values
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 32. Transaction fee breakdown
CREATE TABLE tbl_transaction_fees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    fee_model_id INT NOT NULL,
    calculated_amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transaction_id (transaction_id)
);

-- 33. Transaction disputes
CREATE TABLE tbl_transaction_disputes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    reporting_user_id BIGINT NOT NULL,
    dispute_reason TEXT NOT NULL,
    dispute_status VARCHAR(50) NOT NULL DEFAULT 'OPEN', -- OPEN, IN_REVIEW, RESOLVED, REJECTED
    resolution_notes TEXT,
    resolved_by_admin_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_transaction_id (transaction_id)
);

-- 34. Reversal and void logs
CREATE TABLE tbl_transaction_reversals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_transaction_id BIGINT NOT NULL,
    reversal_transaction_id BIGINT NOT NULL,
    reason TEXT NOT NULL,
    reversed_by_admin_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_original_transaction_id (original_transaction_id)
);

-- 35. Transaction metadata (e.g., device used, location)
CREATE TABLE tbl_transaction_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    meta_key VARCHAR(100) NOT NULL,
    meta_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tran_meta_key (transaction_id, meta_key)
);

-- 36. User's linked bank accounts
CREATE TABLE tbl_user_bank_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bank_name VARCHAR(100) NOT NULL,
    account_number_hash VARCHAR(255) NOT NULL,
    account_number_last4 VARCHAR(4) NOT NULL,
    account_holder_name VARCHAR(255) NOT NULL,
    routing_number VARCHAR(50), -- e.g., ABA, SWIFT/BIC
    account_type VARCHAR(50), -- e.g., CHECKING, SAVINGS
    verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    is_primary_cashout BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 37. User's linked credit/debit cards
CREATE TABLE tbl_user_credit_cards (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    payment_gateway_token VARCHAR(255) NOT NULL, -- Token from Stripe, etc.
    card_brand VARCHAR(50), -- e.g., VISA, MASTERCARD
    card_last4 VARCHAR(4) NOT NULL,
    expiry_month INT NOT NULL,
    expiry_year INT NOT NULL,
    cardholder_name VARCHAR(255),
    is_primary_cashin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 38. Cash-in requests (e.g., via card, bank transfer)
CREATE TABLE tbl_cash_in_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cash_in_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    funding_source_type VARCHAR(50), -- e.g., CARD, BANK_TRANSFER, OTC
    funding_source_id BIGINT, -- e.g., tbl_user_credit_cards.id
    status VARCHAR(50) NOT NULL, -- PENDING, COMPLETED, FAILED
    payment_gateway_ref VARCHAR(255),
    related_transaction_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_cash_in_uuid (cash_in_uuid)
);

-- 39. Cash-out (withdrawal) requests
CREATE TABLE tbl_cash_out_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cash_out_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    destination_type VARCHAR(50), -- e.g., BANK_ACCOUNT, OTC_PICKUP
    destination_id BIGINT, -- e.g., tbl_user_bank_accounts.id
    status VARCHAR(50) NOT NULL, -- PENDING_APPROVAL, PENDING_DISBURSEMENT, COMPLETED, FAILED
    payout_batch_id BIGINT,
    related_transaction_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_cash_out_uuid (cash_out_uuid)
);

-- 40. List of Over-The-Counter (OTC) cash-in partners
CREATE TABLE tbl_cash_in_partners (
    id INT AUTO_INCREMENT PRIMARY KEY,
    partner_name VARCHAR(100) NOT NULL,
    partner_code VARCHAR(50) NOT NULL UNIQUE,
    api_endpoint VARCHAR(512),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 41. List of cash-out partners
CREATE TABLE tbl_cash_out_partners (
    id INT AUTO_INCREMENT PRIMARY KEY,
    partner_name VARCHAR(100) NOT NULL,
    partner_code VARCHAR(50) NOT NULL UNIQUE,
    api_endpoint VARCHAR(512),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 42. Bank transfer details (e.g., from bank statement)
CREATE TABLE tbl_bank_transfers_in (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_reference_id VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT, -- Matched user
    wallet_id BIGINT, -- Matched wallet
    sender_name VARCHAR(255),
    sender_account_number VARCHAR(100),
    amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    transaction_date TIMESTAMP,
    status VARCHAR(50) DEFAULT 'PENDING_MATCH', -- PENDING_MATCH, MATCHED, ERROR
    cash_in_request_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 43. Payment gateway configuration
CREATE TABLE tbl_payment_gateways (
    id INT AUTO_INCREMENT PRIMARY KEY,
    gateway_name VARCHAR(100) NOT NULL, -- e.g., Stripe, PayPal
    gateway_code VARCHAR(50) NOT NULL UNIQUE,
    config_json JSON, -- API keys, webhooks
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 44. Log of all payment gateway interactions
CREATE TABLE tbl_pg_transaction_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    gateway_id INT NOT NULL,
    request_type VARCHAR(100), -- e.g., CHARGE, REFUND, TOKENIZE
    cash_in_request_id BIGINT,
    request_payload TEXT,
    response_payload TEXT,
    http_status_code INT,
    gateway_transaction_id VARCHAR(255),
    status VARCHAR(50), -- e.g., SUCCESS, FAILURE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cash_in_request_id (cash_in_request_id)
);

-- 45. Batches for mass payouts
CREATE TABLE tbl_payout_batches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_uuid VARCHAR(36) NOT NULL UNIQUE,
    payout_partner_id INT,
    status VARCHAR(50) NOT NULL, -- e.g., PENDING, PROCESSING, COMPLETED
    total_amount DECIMAL(18, 4),
    total_requests INT,
    created_by_admin_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- 46. Direct debit mandates
CREATE TABLE tbl_direct_debit_mandates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_bank_account_id BIGINT NOT NULL,
    mandate_reference VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL, -- e.g., PENDING, ACTIVE, FAILED, CANCELLED
    provider VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 47. Merchant business entity
CREATE TABLE tbl_merchants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_uuid VARCHAR(36) NOT NULL UNIQUE,
    business_name VARCHAR(255) NOT NULL,
    business_registration_number VARCHAR(100),
    business_type VARCHAR(100), -- e.g., SOLE_PROP, LLC
    merchant_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant_uuid (merchant_uuid)
);

-- 48. Primary user(s) associated with a merchant
CREATE TABLE tbl_merchant_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL, -- Links to tbl_users
    merchant_role_id INT NOT NULL, -- e.g., Owner, Admin, Cashier
    is_primary_contact BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant_user (merchant_id, user_id)
);

-- 49. Roles for merchant users
CREATE TABLE tbl_merchant_roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(100) NOT NULL, -- e.g., OWNER, ADMIN, CASHIER, READ_ONLY
    permissions_json JSON, -- Stores what this role can do
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 50. Physical or virtual store locations
CREATE TABLE tbl_merchant_stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    store_name VARCHAR(255) NOT NULL,
    store_code VARCHAR(50) UNIQUE,
    address_line1 VARCHAR(255),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    country_code VARCHAR(3),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id)
);

-- 51. API keys for merchants
CREATE TABLE tbl_merchant_api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    api_key_public VARCHAR(255) NOT NULL UNIQUE,
    api_key_secret_hash VARCHAR(255) NOT NULL,
    key_type VARCHAR(20) NOT NULL, -- e.g., LIVE, TEST
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMP,
    INDEX idx_merchant_id (merchant_id)
);

-- 52. Webhook/Callback configurations
CREATE TABLE tbl_merchant_callbacks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL, -- e.g., PAYMENT_SUCCESS, REFUND_PROCESSED
    callback_url VARCHAR(512) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id)
);

-- 53. Log of callback attempts
CREATE TABLE tbl_merchant_callback_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    callback_id BIGINT NOT NULL,
    transaction_id BIGINT,
    attempt_count INT DEFAULT 1,
    request_payload TEXT,
    response_body TEXT,
    response_status_code INT,
    status VARCHAR(50) NOT NULL, -- e.g., SUCCESS, FAILED, PENDING_RETRY
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_callback_id (callback_id)
);

-- 54. Merchant settlement accounts (bank)
CREATE TABLE tbl_merchant_settlement_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    bank_account_id BIGINT NOT NULL, -- Links to tbl_user_bank_accounts (or a separate tbl_merchant_bank_accounts)
    currency_code VARCHAR(3) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id)
);

-- 55. Log of settlements paid to merchants
CREATE TABLE tbl_merchant_settlement_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    settlement_batch_id BIGINT,
    settlement_account_id BIGINT NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL, -- e.g., PENDING, PROCESSING, COMPLETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id)
);

-- 56. Business documents for merchant onboarding
CREATE TABLE tbl_merchant_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    document_type VARCHAR(100) NOT NULL, -- e.g., BUSINESS_REGISTRATION, TAX_ID
    document_url VARCHAR(512) NOT NULL,
    verification_status VARCHAR(50) DEFAULT 'PENDING',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id)
);

-- 57. QR codes for merchants/stores
CREATE TABLE tbl_merchant_qr_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    store_id BIGINT, -- Nullable if for the merchant directly
    qr_code_uuid VARCHAR(36) NOT NULL UNIQUE,
    qr_code_data TEXT NOT NULL,
    qr_code_image_url VARCHAR(512),
    qr_type VARCHAR(50) NOT NULL, -- e.g., STATIC, DYNAMIC
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_merchant_id (merchant_id)
);

-- 58. Terminals/POS devices at stores
CREATE TABLE tbl_store_terminals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id BIGINT NOT NULL,
    terminal_id_hash VARCHAR(255) NOT NULL UNIQUE,
    terminal_name VARCHAR(100),
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_store_id (store_id)
);

-- 59. List of billers (utilities, etc.)
CREATE TABLE tbl_billers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    biller_code VARCHAR(100) NOT NULL UNIQUE,
    biller_name VARCHAR(255) NOT NULL,
    biller_category_id INT NOT NULL,
    service_fee DECIMAL(18, 4) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 60. Categories for billers
CREATE TABLE tbl_biller_categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 61. Log of bill payment transactions
CREATE TABLE tbl_bill_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_payment_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    biller_id INT NOT NULL,
    account_number VARCHAR(100) NOT NULL, -- e.g., utility account number
    amount DECIMAL(18, 4) NOT NULL,
    fee_amount DECIMAL(18, 4) NOT NULL,
    total_amount DECIMAL(18, 4) NOT NULL,
    transaction_id BIGINT NOT NULL, -- Links to tbl_transactions
    status VARCHAR(50) NOT NULL, -- e.g., PENDING, SUCCESS, FAILED
    biller_reference_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_transaction_id (transaction_id)
);

-- 62. E-Load (mobile top-up) products
CREATE TABLE tbl_eload_products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(100) NOT NULL UNIQUE,
    telco_provider VARCHAR(50), -- e.g., GLOBE, SMART
    description VARCHAR(255),
    face_value DECIMAL(18, 4) NOT NULL,
    cost_price DECIMAL(18, 4) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 63. Log of e-load transactions
CREATE TABLE tbl_eload_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    eload_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    eload_product_id INT NOT NULL,
    target_phone_number VARCHAR(20) NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    transaction_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL, -- e.g., PENDING, SUCCESS, FAILED
    provider_reference_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_transaction_id (transaction_id)
);

-- 64. Voucher definitions
CREATE TABLE tbl_vouchers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    voucher_code VARCHAR(50) NOT NULL UNIQUE,
    voucher_name VARCHAR(255),
    voucher_type VARCHAR(50), -- e.g., FIXED_AMOUNT, PERCENTAGE, FREE_ITEM
    value DECIMAL(18, 4),
    percent_discount DECIMAL(5, 2),
    max_discount_amount DECIMAL(18, 4),
    min_spend DECIMAL(18, 4),
    total_quantity INT,
    remaining_quantity INT,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_voucher_code (voucher_code)
);

-- 65. Vouchers assigned to users
CREATE TABLE tbl_user_vouchers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE', -- e.g., AVAILABLE, USED, EXPIRED
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP,
    used_transaction_id BIGINT,
    INDEX idx_user_id (user_id)
);

-- 66. Log of voucher redemptions
CREATE TABLE tbl_voucher_redemptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    user_voucher_id BIGINT NOT NULL,
    discount_amount DECIMAL(18, 4) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transaction_id (transaction_id)
);

-- 67. User loyalty points balance
CREATE TABLE tbl_loyalty_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    current_balance BIGINT NOT NULL DEFAULT 0,
    total_earned BIGINT NOT NULL DEFAULT 0,
    total_spent BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 68. Ledger for loyalty points
CREATE TABLE tbl_loyalty_points_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    transaction_id BIGINT,
    reward_claim_id BIGINT,
    points INT NOT NULL, -- Positive for EARN, negative for SPEND
    entry_type VARCHAR(50) NOT NULL, -- e.g., EARN, SPEND, EXPIRE
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 69. Catalog of rewards
CREATE TABLE tbl_rewards_catalog (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reward_name VARCHAR(255) NOT NULL,
    description TEXT,
    points_cost BIGINT NOT NULL,
    reward_type VARCHAR(50), -- e.g., VOUCHER, PHYSICAL_ITEM
    reward_voucher_id BIGINT,
    stock_quantity INT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 70. Log of claimed rewards
CREATE TABLE tbl_reward_claims (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    reward_id INT NOT NULL,
    points_spent BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL, -- e.g., PENDING_FULFILLMENT, COMPLETED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 71. Payment links generated by users
CREATE TABLE tbl_payment_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL, -- The user who gets paid
    link_uuid VARCHAR(36) NOT NULL UNIQUE,
    amount DECIMAL(18, 4), -- Nullable for open amount
    currency_code VARCHAR(3) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- e.g., ACTIVE, PAID, EXPIRED
    expires_at TIMESTAMP,
    resulting_transaction_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 72. Log of QR code payments
CREATE TABLE tbl_qr_code_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    qr_code_id BIGINT NOT NULL,
    payer_wallet_id BIGINT NOT NULL,
    payee_wallet_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_qr_code_id (qr_code_id)
);

-- 73. User login history
CREATE TABLE tbl_user_login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_id BIGINT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    login_type VARCHAR(50), -- e.g., PIN, BIOMETRIC, PASSWORD
    status VARCHAR(50), -- e.g., SUCCESS, FAILED_PIN
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 74. User web/app sessions
CREATE TABLE tbl_user_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    device_id BIGINT,
    ip_address VARCHAR(45),
    is_active BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 75. One-Time Passwords (OTP)
CREATE TABLE tbl_otp_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    otp_code_hash VARCHAR(255) NOT NULL,
    otp_action VARCHAR(50) NOT NULL, -- e.g., LOGIN, RESET_PIN, WITHDRAWAL
    channel VARCHAR(20) NOT NULL, -- e.g., SMS, EMAIL
    is_used BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 76. User security questions
CREATE TABLE tbl_user_security_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question_id INT NOT NULL,
    answer_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 77. Master list of security questions
CREATE TABLE tbl_security_questions_master (
    id INT AUTO_INCREMENT PRIMARY KEY,
    question_text VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    locale VARCHAR(10) DEFAULT 'en'
);

-- 78. User PIN/password history
CREATE TABLE tbl_password_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 79. AML (Anti-Money Laundering) flags
CREATE TABLE tbl_aml_flags (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    transaction_id BIGINT,
    flag_type VARCHAR(100) NOT NULL, -- e.g., HIGH_VELOCITY, UNUSUAL_AMOUNT, WATCHLIST_HIT
    risk_score INT,
    status VARCHAR(50) DEFAULT 'OPEN', -- e.g., OPEN, IN_REVIEW, CLEARED
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_transaction_id (transaction_id)
);

-- 80. Watchlists for AML screening
CREATE TABLE tbl_aml_watchlists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50), -- e.g., PERSON, ORGANIZATION
    list_source VARCHAR(100), -- e.g., OFAC, EU
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_entity_name (entity_name)
);

-- 81. System-wide audit logs
CREATE TABLE tbl_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT, -- Can be admin or system user
    action VARCHAR(255) NOT NULL, -- e.g., UPDATED_USER_STATUS
    target_entity VARCHAR(100), -- e.g., tbl_users
    target_entity_id BIGINT,
    before_value TEXT,
    after_value TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target_entity_id (target_entity_id)
);

-- 82. Security events and alerts
CREATE TABLE tbl_security_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_code VARCHAR(100) NOT NULL, -- e.g., FAILED_LOGIN_SPAM, PIN_RESET_INITIATED
    user_id BIGINT,
    device_id BIGINT,
    ip_address VARCHAR(45),
    event_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 83. User 2FA (Two-Factor Auth) settings
CREATE TABLE tbl_two_factor_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    method VARCHAR(50), -- e.g., TOTP, SMS
    totp_secret_encrypted VARCHAR(512),
    is_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 84. Blocked lists (IP, email, phone)
CREATE TABLE tbl_blocked_entities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL, -- e.g., IP, EMAIL, PHONE, DEVICE_ID
    entity_value_hash VARCHAR(255) NOT NULL,
    reason TEXT,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_entity_type (entity_type)
);

-- 85. Internal admin users
CREATE TABLE tbl_admin_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    admin_role_id INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 86. Roles for admin users
CREATE TABLE tbl_admin_roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(100) NOT NULL UNIQUE, -- e.g., SUPER_ADMIN, CS_AGENT, COMPLIANCE_OFFICER
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 87. Permissions definitions
CREATE TABLE tbl_admin_permissions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL UNIQUE, -- e.g., CAN_VIEW_USERS, CAN_FREEZE_WALLET
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 88. Mapping roles to permissions
CREATE TABLE tbl_admin_role_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_role_id INT NOT NULL,
    admin_permission_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_perm (admin_role_id, admin_permission_id)
);

-- 89. Customer support tickets
CREATE TABLE tbl_support_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    assigned_admin_id BIGINT,
    subject VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    ticket_status VARCHAR(50) NOT NULL, -- e.g., OPEN, IN_PROGRESS, RESOLVED, CLOSED
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    category VARCHAR(100), -- e.g., TRANSACTION_DISPUTE, KYC_HELP
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_assigned_admin_id (assigned_admin_id)
);

-- 90. Replies to support tickets
CREATE TABLE tbl_support_ticket_replies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    user_id BIGINT, -- Null if reply is from admin
    admin_user_id BIGINT, -- Null if reply is from user
    reply_body TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ticket_id (ticket_id)
);

-- 91. System-wide announcements
CREATE TABLE tbl_system_announcements (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    audience VARCHAR(50) DEFAULT 'ALL', -- e.g., ALL, MERCHANTS, UNVERIFIED_USERS
    display_from TIMESTAMP,
    display_to TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_by_admin_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 92. Key-value store for system configuration
CREATE TABLE tbl_system_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description TEXT,
    is_encrypted BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by_admin_id BIGINT
);

-- 93. In-app notifications
CREATE TABLE tbl_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(100), -- e.g., TRANSACTION_SUCCESS, PROMO
    title VARCHAR(255),
    body TEXT NOT NULL,
    cta_link VARCHAR(512),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);

-- 94. General API request/response logs
CREATE TABLE tbl_api_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_uuid VARCHAR(36) NOT NULL,
    endpoint VARCHAR(512) NOT NULL,
    method VARCHAR(10) NOT NULL,
    user_id BIGINT,
    merchant_id BIGINT,
    ip_address VARCHAR(45),
    request_headers TEXT,
    request_body TEXT,
    response_status_code INT,
    response_body TEXT,
    duration_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created_at (created_at),
    INDEX idx_user_id (user_id)
);

-- 95. System-level error logs
CREATE TABLE tbl_error_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(100), -- e.g., 'payment-service', 'user-service'
    error_code VARCHAR(50),
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    context_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created_at (created_at)
);

-- 96. Daily summary of transactions
CREATE TABLE tbl_report_daily_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_date DATE NOT NULL UNIQUE,
    total_volume DECIMAL(18, 4),
    total_count BIGINT,
    transaction_type_id INT,
    currency_code VARCHAR(3),
    average_value DECIMAL(18, 4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_type_curr (report_date, transaction_type_id, currency_code)
);

-- 97. Daily user signups
CREATE TABLE tbl_report_daily_signups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    report_date DATE NOT NULL UNIQUE,
    signup_count INT NOT NULL,
    kyc_completed_count INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 98. Pre-computed merchant revenue
CREATE TABLE tbl_report_merchant_daily_revenue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_date DATE NOT NULL,
    merchant_id BIGINT NOT NULL,
    total_volume_received DECIMAL(18, 4),
    total_transactions_received INT,
    total_fees_paid DECIMAL(18, 4),
    currency_code VARCHAR(3),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_merchant_curr (report_date, merchant_id, currency_code)
);

-- 99. System balance / treasury report
CREATE TABLE tbl_report_system_balances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_datetime TIMESTAMP NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    total_user_float DECIMAL(18, 4) NOT NULL, -- Sum of all user balances
    total_merchant_float DECIMAL(18, 4) NOT NULL,
    fee_wallet_balance DECIMAL(18, 4) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_datetime_curr (report_datetime, currency_code)
);

-- 100. Email and SMS notification logs
CREATE TABLE tbl_notification_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    channel VARCHAR(10) NOT NULL, -- e.g., SMS, EMAIL
    destination VARCHAR(255) NOT NULL, -- Masked phone/email
    template_code VARCHAR(100),
    content TEXT,
    provider VARCHAR(100), -- e.g., Twilio, SendGrid
    provider_message_id VARCHAR(255),
    status VARCHAR(50) NOT NULL, -- e.g., SENT, FAILED, DELIVERED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
);