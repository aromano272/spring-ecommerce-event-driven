wrk.method = "POST"
wrk.body = '{"userId": 1}'
wrk.headers["Content-Type"] = "application/json"

local first_response_written = false
local total_requests = 0

local first_file = "first_response.tmp"
local last_file = "last_response.tmp"

local function write_file(filename, text)
	local f = io.open(filename, "w")
	if f then
		f:write(text .. "\n")
		f:close()
	end
end

function response(status, headers, body)
	total_requests = total_requests + 1
	local snippet = string.sub(body or "<empty>", 1, 200)

	-- Write first response once
	if not first_response_written then
		write_file(first_file, "FIRST RESPONSE:\nStatus: " .. status .. "\nBody: " .. snippet)
		first_response_written = true
	end

	-- Write last response every 100 requests
	if total_requests % 100 == 0 then
		write_file(last_file, "LAST RESPONSE:\nStatus: " .. status .. "\nBody: " .. snippet)
	end
end

function done(summary, latency, requests)
	local out = io.open("wrk_responses.txt", "w")
	if not out then
		return
	end

	out:write("Total requests (summary): " .. (summary.requests or 0) .. "\n\n")

	-- Append first response if file exists
	local f = io.open(first_file, "r")
	if f then
		out:write(f:read("*a") .. "\n\n")
		f:close()
		os.remove(first_file)
	end

	-- Append last response if file exists
	local l = io.open(last_file, "r")
	if l then
		out:write(l:read("*a") .. "\n\n")
		l:close()
		os.remove(last_file)
	end

	out:close()
end
