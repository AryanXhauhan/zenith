local key        = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now        = tonumber(ARGV[3])
local cost       = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens     = tonumber(data[1]) or max_tokens
local last_refill = tonumber(data[2]) or now

-- Compute tokens to add since last request
local elapsed    = math.max(0, now - last_refill)
local refill     = math.floor(elapsed * refill_rate / 1000)
tokens           = math.min(max_tokens, tokens + refill)

if tokens < cost then
    -- Not enough tokens → reject
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 60)
    return 0
end

tokens = tokens - cost
redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
redis.call('EXPIRE', key, 60)
return 1
