/**
 * API contract documentation using JSDoc typedefs.
 * These types document the expected request/response shapes from the backend.
 * Field names match the actual JSON returned by the backend (camelCase or snake_case as used).
 */

/**
 * @typedef {Object} AuthLoginRequest
 * @property {string} email - User email address
 * @property {string} password - User password
 */

/**
 * @typedef {Object} AuthLoginResponse
 * @property {string} token - JWT authentication token (may be named 'token', 'accessToken', or 'jwt' depending on backend)
 * @property {string} [email] - User email
 * @property {string} [userId] - User ID
 */

/**
 * @typedef {Object} AuthRegisterRequest
 * @property {string} email - User email address
 * @property {string} password - User password
 * @property {string} [username] - Optional username (if backend requires it)
 */

/**
 * @typedef {Object} AuthRegisterResponse
 * @property {string} [token] - JWT authentication token (if backend returns it on registration)
 * @property {string} email - User email
 * @property {string} userId - User ID
 * @property {string} [message] - Success message
 */

/**
 * @typedef {Object} UserMeResponse
 * @property {string} id - User ID (UUID)
 * @property {string} email - User email address
 * @property {string} [createdAt] - Account creation timestamp
 * @property {string} [updatedAt] - Last update timestamp
 */

/**
 * @typedef {Object} AssetListDto
 * @property {string} id - Asset ID (UUID)
 * @property {string} symbol - Asset symbol (e.g., 'BTC', 'ETH')
 * @property {string} name - Asset name (e.g., 'Bitcoin', 'Ethereum')
 * @property {number} scale - Decimal precision/scale for the asset
 * @property {number|null} priceUsd - Current price in USD (null if unavailable)
 * @property {number|null} change24hPercent - 24h price change percentage (null if unavailable)
 * @property {boolean} priceUnavailable - True if price data could not be fetched
 * @property {string} updatedAt - Timestamp of last price update
 */

/**
 * @typedef {Object} AssetDetailDto
 * @property {string} id - Asset ID (UUID)
 * @property {string} symbol - Asset symbol
 * @property {string} name - Asset name
 * @property {number} scale - Decimal precision
 * @property {number|null} priceUsd - Current price in USD
 * @property {number|null} change24hPercent - 24h change %
 * @property {number|null} highPrice24h - 24h high
 * @property {number|null} lowPrice24h - 24h low
 * @property {number|null} volume24h - 24h trading volume
 * @property {boolean} priceUnavailable - True if Binance is down
 * @property {string} updatedAt - Timestamp
 */

/**
 * @typedef {Object} PagedResponse
 * @property {Array} items - Page items
 * @property {number} total - Total item count
 * @property {number} page - Current page (0-based)
 * @property {number} size - Page size
 * @property {number} totalPages - Total pages
 */

/**
 * @typedef {Object} PositionDto
 * @property {string} symbol - Asset symbol
 * @property {string} name - Asset name
 * @property {number} quantity - Total quantity held
 * @property {number} availableQuantity - Available (not locked) quantity
 * @property {number} lockedQuantity - Locked quantity (in open orders)
 * @property {number|null} currentPriceUsd - Current market price
 * @property {number|null} marketValueUsd - Total market value
 */

/**
 * @typedef {Object} TransactionDto
 * @property {string} id - Transaction ID (UUID)
 * @property {string} type - Transaction type (e.g., 'DEPOSIT', 'WITHDRAWAL', 'TRADE', 'ORDER')
 * @property {string} amount - Transaction amount (as string or number)
 * @property {string} [currency] - Currency symbol
 * @property {string} [status] - Transaction status
 * @property {string} [createdAt] - Transaction timestamp
 * @property {string} [userId] - Associated user ID
 */
