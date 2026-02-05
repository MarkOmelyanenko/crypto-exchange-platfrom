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
 * @typedef {Object} AssetDto
 * @property {string} id - Asset ID (UUID)
 * @property {string} symbol - Asset symbol (e.g., 'BTC', 'ETH')
 * @property {string} name - Asset name (e.g., 'Bitcoin', 'Ethereum')
 * @property {number} scale - Decimal precision/scale for the asset
 * @property {string} [createdAt] - Asset creation timestamp
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
