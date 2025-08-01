-- Configure POST request
wrk.method = "POST"
wrk.body = '{"userId": 1}'
wrk.headers["Content-Type"] = "application/json"

-- Variables to store first and last responses
local first_response = nil
local last_response = nil
local total_requests = 0

response = function(status, headers, body)
	total_requests = total_requests + 1

	-- Save the first response only once
	if first_response == nil then
		first_response = "FIRST RESPONSE:\nStatus: " .. status .. "\nBody: " .. string.sub(body or "", 1, 500)
	end

	-- Continuously overwrite last response
	last_response = "LAST RESPONSE:\nStatus: " .. status .. "\nBody: " .. string.sub(body or "", 1, 500)
end

done = function(summary, latency, requests)
	local file = io.open("wrk_responses.txt", "w")
	file:write("Total requests: " .. total_requests .. "\n\n")
	if first_response ~= nil then
		file:write(first_response .. "\n\n")
	else
		file:write("No first response captured\n\n")
	end

	if last_response ~= nil then
		file:write(last_response .. "\n")
	else
		file:write("No last response captured\n")
	end

	file:close()
end
