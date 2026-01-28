# from sentence_transformers import SentenceTransformer
import os, json #, re, math
# import numpy as np
from openai import OpenAI

client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])

def merge_stream(memos, style_features, style_examples):

    style_features_text, style_examples_text = prep(style_features, style_examples)
    paragraphs = "\n\n".join(f"<p>{m}</p>" for m in memos)

    developer_msg = """
You are a diary editor that merges multiple diary paragraphs into one coherent diary entry.

Rules:
- Preserve the original wording of each paragraph as much as possible.
- Do NOT rewrite paragraphs unless needed for smooth transitions.
- Maintain a consistent writing style and tone across the entire diary.
- Ensure natural flow between paragraphs using subtle transitions.
- Do not summarize or condense content.

Style handling:
- Follow the provided style features.
- The given paragraphs already represent the target style.
- Do not introduce a new voice or tone.

Output constraints:
Output constraints:
- Output plain Korean prose.
- Preserve paragraph breaks.
- Separate paragraphs with a blank line (double newline).
- Each paragraph should be visually separated.
- No titles, no bullet points, no meta commentary.
"""

    prompt = f"""
<style_features>
{style_features_text}
</style_features>

<paragraphs>
{paragraphs}
</paragraphs>

Task:
Merge the paragraphs into a complete diary entry with smooth transitions and consistent style.
"""

    input_len = sum(len(m) for m in memos)
    estimated_input_tokens = int(input_len * 0.35)
    max_tokens = estimated_input_tokens + 200

    stream = client.chat.completions.create(
        model=os.getenv("GPT_MODEL", "gpt-4.1-nano"),
        stream=True,
        messages=[
            {"role": "developer", "content": developer_msg},
            {"role": "user",  "content": prompt},
        ],
        temperature=0.8,
        max_tokens=max_tokens,
    )

    for chunk in stream:
        delta = chunk.choices[0].delta
        text = delta.content or ""
        if text:
            yield chunk

def merge_paragraph_stream(memos, style_features, style_examples, length_level=1):

    style_features_text, style_examples_text = prep(style_features, style_examples)
    memo_body = memos[0]
    memo_piece = memos[1]

    developer_msg = """
You are a writing assistant that merges two related memo texts into a single, natural diary paragraph.

Rules:
- The output must be ONE paragraph of diary-style prose.
- Do NOT explain the process or mention memos.
- Base memo is the core narrative. Piece memo adds context, emotion, or detail.
- The two texts describe related events and must be smoothly connected.
- Preserve the user's writing style strictly.

Style handling:
- Follow the provided style features and imitate the style examples.
- Do not copy sentences verbatim from the examples.

Length control:
- length_level = 0 → very concise, minimal expansion
- length_level = 1 → normal diary paragraph
- length_level = 2 → detailed, reflective, richer description

Output:
- Natural Korean prose.
- No titles, no bullet points, no markdown.
"""


    prompt = f"""
<style_features>
{style_features_text}
</style_features>

<style_examples>
{style_examples_text}
</style_examples>

<base_memo>
{memo_body}
</base_memo>

<piece_memo>
{memo_piece}
</piece_memo>

<length_level>
{length_level}
</length_level>

Task:
Merge the base memo and the piece memo into a single diary paragraph that follows the specified style and length.
"""

    base_tokens = len(memo_body + memo_piece) // 2
    max_tokens = {
        0: base_tokens + 50,
        1: base_tokens + 150,
        2: base_tokens + 300,
    }.get(length_level, base_tokens + 150)

    stream = client.chat.completions.create(
        model=os.getenv("GPT_MODEL", "gpt-4.1-nano"),
        stream=True,
        messages=[
            {"role": "developer", "content": developer_msg},
            {"role": "user",  "content": prompt},
        ],
        temperature=0.8,
        max_tokens=max_tokens,
    )

    for chunk in stream:
        delta = chunk.choices[0].delta
        text = delta.content or ""
        if text:
            yield chunk

def prep(style_features, style_examples):
    if isinstance(style_features, dict):
        style_features_text = json.dumps(style_features, ensure_ascii=False, indent=2)
    else:
        style_features_text = str(style_features)

    if isinstance(style_examples, list):
        style_examples_text = "\n".join(f"- {s}" for s in style_examples)
    else:
        style_examples_text = "- " + str(style_examples)

    return style_features_text, style_examples_text


# ##### 더 정확한 스타일 적용
# embed_model = SentenceTransformer("jhgan/ko-sroberta-multitask")

# ### ---- helper functions ---- ###
# def l2norm(x):
#     return x / (np.linalg.norm(x, axis=-1, keepdims=True) + 1e-12)

# def embed_sentences(sentences):
#     E = embed_model.encode(sentences, convert_to_numpy=True, batch_size=64)
#     return l2norm(E)

# def choose_best_sentence(candidates, style_vec):
#     if not candidates:
#         return None

#     E = embed_sentences(candidates)  # (N, D)
#     sims = (E @ style_vec.reshape(-1, 1)).reshape(-1)  # (N,)
#     best_idx = int(np.argmax(sims))
#     return candidates[best_idx]

# def count_sentences(text: str) -> int:
#     text = text.strip()
#     if not text:
#         return 0
#     sentences = re.split(r'[.!?]+\s*|\n+', text)
#     sentences = [s for s in sentences if s.strip()]
#     return len(sentences)

# ### ---- merge function (메모 단위 후보 생성/선택) ---- ###
# def merge_rerank(memos, style_prompt, style_examples, style_vector, num_candidates: int = 3, temperature: float = 0.8):

#     # 프롬프트 작성용 정리
#     if isinstance(style_prompt, dict):
#         style_prompt_text = json.dumps(style_prompt, ensure_ascii=False, indent=2)
#     else:
#         style_prompt_text = str(style_prompt)

#     if isinstance(style_examples, list):
#         style_examples_text = "\n".join(f"- {s}" for s in style_examples)
#     else:
#         style_examples_text = "- " + str(style_examples)

#     # style_vector 정규화
#     style_vec = np.array(style_vector, dtype=np.float32).reshape(1, -1)
#     style_vec = l2norm(style_vec)[0]  # (D,)

#     indexed_memos_text = "\n".join(f"{i+1}. {m}" for i, m in enumerate(memos))
#     accumulated_diary = ""

#     system_msg = """
# You are a diary-writing assistant.

# Your priorities are, in this exact order:
# 1) FAITHFULLY reflect the concrete events from the memos.
# 2) Keep the chronological order of the memos.
# 3) Then, softly adjust tone and style to match the user's profile.

# You MUST NOT invent events that are not clearly implied by the memos.
# Every paragraph you write must be grounded in the given focus memo.
# You are currently writing the diary ONE MEMO AT A TIME, memo by memo.
# """

#     for idx, memo in enumerate(memos):
#         # 길이제한: 글자수로
#         base_len = len(memo.strip())
#         min_len = int(base_len * 1.3)
#         max_len = int(base_len * 1.8)

#         if min_len < 10: min_len = 10
#         if max_len < 15: max_len = 20

#         current_diary_block = (
#             accumulated_diary if accumulated_diary.strip()
#             else "(nothing yet)"
#         )

#         focus_memo_idx = idx + 1
#         focus_memo = memo

#         prompt = f"""
# You are a diary writing assistant.

# You will be given:
# - ONE memo: a fragmented note the user wrote today
# - style profile: JSON describing the user's writing tone, phrasing preference, pacing, common expressions
# - style examples: several representative sentences the user has written before
# - the diary text that has already been written for previous memos

# Your PRIMARY job for THIS STEP:
# - Take the given memo and rewrite/expand it into a short diary-style paragraph.
# - Preserve the concrete events and facts from the memo (time, place, actions, feelings).
# - Do NOT invent new events that are not clearly implied by the memo.

# Your SECONDARY job:
# - Softly adjust the tone, rhythm, and sentence endings to match the style profile and examples.
# - Style should never override the factual content of the memo.

# ---

# STYLE PROFILE (JSON):
# {style_prompt_text}

# STYLE EXAMPLES (for tone only, NOT for events):
# {style_examples_text}

# ---

# ALL MEMOS (IN ORDER, WITH INDEX):
# {indexed_memos_text}

# CURRENT DIARY SO FAR (previous memos already processed):
# {current_diary_block}

# FOCUS MEMO FOR THIS STEP (memo #{focus_memo_idx}):
# \"\"\"{focus_memo}\"\"\"

# ---

# LENGTH RULE FOR THIS MEMO:
# - The rewritten paragraph for this memo should be between{min_len} and {max_len} characters (including spaces).
# - It should feel concise and natural, not repetitive.
# - It should primarily focus on the events and feelings from the FOCUS MEMO.

# Now generate {num_candidates} DIFFERENT candidate diary-style paragraphs
# that rewrite ONLY this focus memo in a natural diary style.

# Output format (IMPORTANT):
# - Each candidate paragraph must be separated by a line containing only: ###
# - Inside each candidate, just write the sentences, no numbering, no extra commentary.
# """

#         completion = client.chat.completions.create(
#             model=os.getenv("GPT_MODEL", "gpt-4.1-nano"),
#             messages=[
#                 {"role": "system", "content": system_msg},
#                 {"role": "user",  "content": prompt}
#             ],
#             temperature=temperature,
#             max_tokens=512,
#         )

#         raw = completion.choices[0].message.content or ""
#         # "###" 단위로 후보 단락 나누기
#         blocks = [blk.strip() for blk in raw.split("###") if blk.strip()]

#         # 이 메모에 대해 생성 실패 시 그냥 스킵
#         if not blocks:
#             continue

#         # 스타일 벡터와 가장 가까운 단락 선택
#         best_paragraph = choose_best_sentence(blocks, style_vec)
#         if not best_paragraph:
#             continue

#         # 이미 동일 단락이 들어가 있으면 스킵
#         if best_paragraph in accumulated_diary:
#             continue

#         # 누적 일기에 단락 추가
#         if accumulated_diary:
#             accumulated_diary += "\n\n" + best_paragraph.strip()
#         else:
#             accumulated_diary = best_paragraph.strip()

#     return accumulated_diary
