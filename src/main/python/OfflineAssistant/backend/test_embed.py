import sys
from backend.app.services.ai_client import embed_client

if not embed_client.health():
    print("embedding 服务未启动，请先跑 start_embed.bat")
    sys.exit(1)

vecs = embed_client.embed(["猫喜欢吃鱼", "狗喜欢吃肉", "机器学习很有意思"])
print("向量维度:", len(vecs[0]))
print("相似度(0,1):", round(embed_client.find_similar(vecs[0], vecs[1]), 4))
print("相似度(0,2):", round(embed_client.find_similar(vecs[0], vecs[2]), 4))