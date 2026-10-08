// Native regression for the llama.rn 0.13 structured-action bridge.
// Compile against the pinned node_modules/llama.rn/vendor/llama.cpp libraries.
#include "chat.h"
#include "json-schema-to-grammar.h"
#include "llama-grammar.h"

#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>

static void require(bool condition, const char * message) {
    if (!condition) throw std::runtime_error(message);
}

int main() {
    const auto schema = common_json::parse(R"JSON({"oneOf":[
        {"type":"object","additionalProperties":false,"required":["tool","arguments"],"properties":{
            "tool":{"type":"string","enum":["workspace"]},
            "arguments":{"type":"object","required":["operation","content"],"additionalProperties":false,
                "properties":{"operation":{"type":"string","enum":["write_text"]},"content":{"type":"string"}}}}},
        {"type":"object","additionalProperties":false,"required":["answer"],"properties":{"answer":{"type":"string"}}}
    ]})JSON");
    const auto grammar = json_schema_to_grammar(schema);
    require(common_grammar_needs_prefill({COMMON_GRAMMAR_TYPE_OUTPUT_FORMAT, grammar}),
            "Pinned sampler must prefill output-format grammars");
    using grammar_ptr = std::unique_ptr<llama_grammar, decltype(&llama_grammar_free_impl)>;
    const auto fresh = [&]() {
        grammar_ptr result(llama_grammar_init_impl(nullptr, grammar.c_str(), "root", false, nullptr, 0, nullptr, 0),
                           llama_grammar_free_impl);
        require(result != nullptr, "Cannot initialize the native action grammar");
        return result;
    };
    for (const std::string prefix : {"<|im_start|>assistant\n", "<start_of_turn>model\n", "<think>\n"}) {
        auto before = fresh();
        bool rejected = false;
        try {
            // The old bridge supplied this non-JSON text as generation_prompt.
            llama_grammar_accept_str(*before, prefix);
        } catch (const std::exception & original) {
            require(std::string(original.what()).find("empty grammar stack") != std::string::npos,
                    "Expected the native prefill grammar failure");
            try {
                // sampling.cpp rethrows by value, losing the original message.
                throw original;
            } catch (const std::exception & sliced) {
                require(std::string(sliced.what()) == "std::exception", "Expected sliced native exception");
                rejected = true;
            }
        }
        require(rejected, "Template prefix must not be accepted as JSON output");
    }
    for (const std::string json : {
        R"JSON({"answer":"Готово — українська відповідь"})JSON",
        R"JSON({"tool":"workspace","arguments":{"operation":"write_text","content":"<!DOCTYPE html><h1>Україна</h1>"}})JSON"
    }) {
        auto after = fresh();
        // The fixed bridge clears generation_prompt; only new output is accepted.
        llama_grammar_accept_str(*after, json);
        require(!llama_grammar_get_stacks(after.get()).empty(), "Valid JSON must be accepted");
        common_chat_parser_params parser;
        parser.format = COMMON_CHAT_FORMAT_CONTENT_ONLY;
        require(common_chat_parse(json, false, parser).content == json,
                "Raw JSON must reach host validation without a template parser");
    }
    std::cout << "PASS: native prefix rejection reproduces std::exception; raw Ukrainian JSON and HTML actions pass\n";
}
